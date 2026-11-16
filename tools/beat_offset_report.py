#!/usr/bin/env python3
"""
beat_offset_report.py — Beat Accuracy Checker 의 beat_analysis_records.json 에서
"앱 비트가 GT 비트와 어떻게 어긋나는지"를 곡 단위로 진단한다 (표준 라이브러리만 사용).

  python beat_offset_report.py <beat_analysis_records.json> [곡 이름 일부] [엔진 이름 일부]
  예) python beat_offset_report.py tools/beat_analysis_records.json "Shape of You" madmom

출력
  1) 허용 오차(50/70/100/150ms)별 F-measure — 70ms 판정이 너무 빡빡한지 확인
  2) 20초 구간별 중앙 오프셋(앱 - GT, ms) — 위상이 갑자기 바뀌는지(점프), 서서히 벌어지는지(드리프트) 확인
  3) 오프셋 추세(ms/분)와 그에 따른 BPM 보정 제안, 일정한 오프셋 여부

오프셋 부호: 음수 = 앱 비트가 GT 보다 빠름, 양수 = 늦음.
"""
import json
import sys
from bisect import bisect_left
from statistics import median

WINDOW_SEC = 20.0


def nearest(sorted_list, x):
    i = bisect_left(sorted_list, x)
    best = None
    for j in (i - 1, i):
        if 0 <= j < len(sorted_list) and (best is None or abs(sorted_list[j] - x) < abs(best - x)):
            best = sorted_list[j]
    return best


def f_measure(app, gt, tol):
    gt_sorted = sorted(gt)
    used = set()
    tp = 0
    for a in sorted(app):
        i = bisect_left(gt_sorted, a)
        cand = [j for j in (i - 1, i) if 0 <= j < len(gt_sorted)]
        if not cand:
            continue
        j = min(cand, key=lambda k: abs(gt_sorted[k] - a))
        if abs(gt_sorted[j] - a) <= tol and j not in used:
            used.add(j)
            tp += 1
    p = tp / len(app) if app else 0.0
    r = tp / len(gt) if gt else 0.0
    return (2 * p * r / (p + r) if p + r else 0.0), p, r


def theil_sen(xs, ys, max_pairs=20000):
    """이상치에 강한 기울기(Theil-Sen). 점이 많으면 일정 간격으로 샘플링한다."""
    n = len(xs)
    if n < 2:
        return 0.0
    step = max(1, n * (n - 1) // 2 // max_pairs)
    slopes, c = [], 0
    for i in range(n):
        for j in range(i + 1, n):
            c += 1
            if c % step == 0 and xs[j] != xs[i]:
                slopes.append((ys[j] - ys[i]) / (xs[j] - xs[i]))
    return median(slopes) if slopes else 0.0


def report(rec):
    beats = rec.get("beats", {})
    app = [float(x) for x in beats.get("app_ms", [])]
    gt = [float(x) for x in beats.get("gt_ms", [])]
    print("=" * 72)
    print(f"{rec.get('audio_file')}  [{rec.get('engine_name')}]  "
          f"앱 {len(app)}개 / GT {len(gt)}개  (체커 허용 오차 {rec.get('tolerance_ms')}ms)")
    if len(app) < 8 or len(gt) < 8:
        print("  비트가 너무 적어 진단할 수 없습니다.")
        return

    # 1) 허용 오차별 F
    print("\n[허용 오차별 F-measure]")
    for tol in (50, 70, 100, 150):
        f, p, r = f_measure(app, gt, tol)
        print(f"  ±{tol:>3}ms  F={f*100:5.1f}%  P={p*100:5.1f}%  R={r*100:5.1f}%")

    # 2) 오프셋 (앱 - 가장 가까운 GT), 반 박자 이상 벗어난 건 위상 오류라 추세 계산에서 제외
    gt_sorted = sorted(gt)
    gaps = sorted(b - a for a, b in zip(sorted(app), sorted(app)[1:]) if 100 < b - a < 3000)
    period = median(gaps) if gaps else 600.0
    bpm_app = 60000.0 / period
    gt_gaps = sorted(b - a for a, b in zip(gt_sorted, gt_sorted[1:]) if 100 < b - a < 3000)
    gp = median(gt_gaps) if gt_gaps else period
    pts = []
    prev_o = None
    for a in sorted(app):
        g = nearest(gt_sorted, a)
        if g is None:
            continue
        o = a - g
        # 드리프트가 반 박자를 넘으면 가장 가까운 GT 비트가 한 칸 건너뛰어 오프셋이 한 박자만큼 접힌다 → 이어 붙인다
        if prev_o is not None:
            while o - prev_o > gp / 2:
                o -= gp
            while prev_o - o > gp / 2:
                o += gp
        prev_o = o
        pts.append((a / 1000.0, o))
    if len(pts) < 8:
        print("\n  GT 와 가까운 비트가 거의 없어 오프셋을 계산할 수 없습니다 (위상/템포가 크게 다름).")
        return
    dur = max(t for t, _ in pts)
    print(f"\n[{WINDOW_SEC:.0f}초 구간별 중앙 오프셋 (ms, 앱-GT)]   앱 BPM ≈ {bpm_app:.2f}")
    win = {}
    for t, o in pts:
        win.setdefault(int(t // WINDOW_SEC), []).append(o)
    prev = None
    max_jump = (0.0, None)
    for k in sorted(win):
        m = median(win[k])
        bar = "#" * min(40, int(abs(m) / 4))
        sign = "-" if m < 0 else "+"
        print(f"  {int(k*WINDOW_SEC)//60}:{int(k*WINDOW_SEC)%60:02d}~  {m:+7.1f}  {sign}{bar}  (n={len(win[k])})")
        if prev is not None and abs(m - prev[1]) > abs(max_jump[0]):
            max_jump = (m - prev[1], (prev[0], k))
        prev = (k, m)

    # 3) 추세와 제안
    xs = [t for t, _ in pts]
    ys = [o for _, o in pts]
    slope = theil_sen(xs, ys)                 # ms/s (무차원 비율 ×1000)
    med = median(ys)
    resid = [y - (med + slope * (x - median(xs))) for x, y in pts]
    spread = median(abs(r) for r in resid)
    print("\n[진단]")
    print(f"  중앙 오프셋 {med:+.1f}ms, 추세 {slope*60:+.1f}ms/분, 추세 제거 후 흩어짐(중앙) {spread:.1f}ms")
    # 위상 점프(계단) vs 서서히 벌어짐(직선): 두 모델 중 잔차가 작은 쪽을 고른다
    best = None
    for i in range(4, len(ys) - 4):
        l, r = ys[:i], ys[i:]
        ml, mr = median(l), median(r)
        res = median([abs(y - ml) for y in l] + [abs(y - mr) for y in r])
        if best is None or res < best[0]:
            best = (res, i, mr - ml)
    step_spread, split_i, jump_ms = best if best else (spread, 0, 0.0)
    drift_total = abs(slope) * dur
    if abs(jump_ms) >= 40 and step_spread < spread * 0.7:
        t_split = xs[split_i]
        print(f"  → 위상 점프: {int(t_split)//60}:{int(t_split)%60:02d} 부근에서 오프셋이 {jump_ms:+.0f}ms 변함 "
              f"(추적기가 위상을 갈아탐). 점프 모델 흩어짐 {step_spread:.1f}ms < 직선 모델 {spread:.1f}ms")
    elif drift_total >= 50:
        new_bpm = bpm_app * (1.0 + slope / 1000.0)
        print(f"  → 템포 드리프트: 곡 전체에서 오프셋이 약 {slope*dur:+.0f}ms 변함 (앱 템포가 GT 와 미세하게 다름).")
        print(f"    BPM 보정 제안: {bpm_app:.2f} → {new_bpm:.2f}  (BPM 입력창에 소수점으로 입력)")
    elif abs(med) >= 40:
        print(f"  → 일정한 오프셋 {med:+.0f}ms (전체가 같은 만큼 앞/뒤로 밀림).")
    else:
        print("  → 큰 위상/템포 문제는 보이지 않습니다. 오차는 GT 자체의 흔들림 수준일 수 있습니다.")
    print()


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    with open(sys.argv[1], encoding="utf-8") as f:
        records = json.load(f)
    name = sys.argv[2].lower() if len(sys.argv) > 2 else ""
    eng = sys.argv[3].lower() if len(sys.argv) > 3 else ""
    hit = [r for r in records
           if name in str(r.get("audio_file", "")).lower()
           and eng in (str(r.get("engine_name", "")) + str(r.get("engine", ""))).lower()]
    if not hit:
        print("조건에 맞는 기록이 없습니다.")
        sys.exit(1)
    for r in hit:
        report(r)


if __name__ == "__main__":
    main()

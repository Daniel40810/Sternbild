# -*- coding: utf-8 -*-
"""Berechnet die Szenen-Anordnung (Position/Skalierung je Figur) und die lokale Projektion."""
import math
import os
from data import CONS, SCENE, NORTH_ORDER, ZOD_ORDER

BY = {c['code']: c for c in CONS}

def figure_stars(c):
    """Eigene Sterne + ueber Linien referenzierte fremde Sterne."""
    st = list(c['stars'])
    for a, b in c['lines']:
        for r in (a, b):
            if isinstance(r, str):
                code, idx = r.split('.')
                s = BY[code]['stars'][int(idx)]
                if s not in st:
                    st.append(s)
    return st

def center(stars):
    sx = sum(math.cos(s[2]*15*math.pi/180) for s in stars); sy = sum(math.sin(s[2]*15*math.pi/180) for s in stars)
    ra0 = (math.atan2(sy, sx)*180/math.pi/15) % 24
    dec0 = sum(s[3] for s in stars)/len(stars)
    return ra0, dec0

def gnomonic(ra, dec, ra0, dec0):
    """Lokale Projektion in Grad; Osten links (x = -xi)."""
    d = math.pi/180
    a, dl, a0, d0 = ra*15*d, dec*d, ra0*15*d, dec0*d
    cosc = math.sin(d0)*math.sin(dl) + math.cos(d0)*math.cos(dl)*math.cos(a-a0)
    xi = math.cos(dl)*math.sin(a-a0)/cosc
    eta = (math.cos(d0)*math.sin(dl) - math.sin(d0)*math.cos(dl)*math.cos(a-a0))/cosc
    return -xi/d, eta/d

def extent(c):
    st = figure_stars(c); ra0, dec0 = center(st)
    pts = [gnomonic(s[2], s[3], ra0, dec0) for s in st]
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    return max(xs)-min(xs), max(ys)-min(ys)

def target(ext):
    return max(14.0, min(23.0, ext*0.8))

def compute():
    items = {}
    W = SCENE['w']
    n = len(NORTH_ORDER)
    for i, code in enumerate(NORTH_ORDER):
        ex, ey = extent(BY[code]); e = max(ex, ey)
        x = 11 + (W-22)/(n-1)*i; y = 16.0 if i % 2 == 0 else 33.0
        items[code] = [round(x, 2), round(y, 2), round(target(e)/e, 4), 0.0]
    m = len(ZOD_ORDER)
    for i, code in enumerate(ZOD_ORDER):
        ex, ey = extent(BY[code]); e = max(ex, ey)
        x = 13 + (W-26)/(m-1)*i
        base = 67 - 9*math.sin(math.pi*x/W)
        y = base + (-9.5 if i % 2 == 0 else 9.5)
        items[code] = [round(x, 2), round(y, 2), round(target(e)/e, 4), 0.0]
    # Plejaden neben den Stier, vergroessert
    tx, ty = items['Tau'][0], items['Tau'][1]
    items["M45"] = [round(tx+2, 2), round(ty-15, 2), 3.6, 0.0]
    for k, v in OVERRIDES.items():
        items[k] = [v[0], v[1], items[k][2] if v[2] is None else v[2], v[3]]
    return items

OVERRIDES = {"And": [175.0, 35.0, None, 0.0], "Peg": [156.0, 16.0, None, 0.0]}

if __name__ == '__main__':
    import matplotlib; matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    items = compute()
    fig, ax = plt.subplots(figsize=(19.2, 10.8), dpi=80)
    ax.set_facecolor('#081338'); ax.set_xlim(0, SCENE['w']); ax.set_ylim(SCENE['h'], 0)
    ax.axhline(SCENE['horizon'], color='#f0c0a0')
    for code, (x, y, s, r) in items.items():
        c = BY[code]; st = figure_stars(c); ra0, dec0 = center(st)
        P = [gnomonic(q[2], q[3], ra0, dec0) for q in st]
        P = [(x + p[0]*s, y - p[1]*s) for p in P]
        def idx(ref):
            if isinstance(ref, str):
                cc, k = ref.split('.'); return st.index(BY[cc]['stars'][int(k)])
            return ref
        for a, b in c['lines']:
            A, B = P[idx(a)], P[idx(b)]
            ax.plot([A[0], B[0]], [A[1], B[1]], color='#8fb4ff', lw=1)
        for (px, py), q in zip(P, st):
            ax.plot(px, py, 'o', color='white', ms=max(1, 6-q[4]))
        ax.text(x, max(p[1] for p in P)+3, c['de'], color='#ccd', ha='center', fontsize=9)
    plt.savefig(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'layout_preview.png'))
    for k, v in items.items(): print(k, v, [round(e, 1) for e in extent(BY[k])])

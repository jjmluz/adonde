#!/usr/bin/env python3
"""Builds shared/src/main/assets/globe.csv: the dots of land on the spinning globe.

Usage: build_globe.py path/to/land-110m.json shared/src/main/assets/globe.csv

The source comes from the world-atlas package, which is Natural Earth's land in TopoJSON.
"""
import json
import math
import sys

DOT_SPACING_DEGREES = 2.6

source, target = sys.argv[1:3]
with open(source, encoding="utf-8") as file:
    topology = json.load(file)
scale_x, scale_y = topology["transform"]["scale"]
origin_x, origin_y = topology["transform"]["translate"]


def decode(arc):
    """TopoJSON stores each point as its distance from the one before."""
    x = y = 0
    points = []
    for step_x, step_y in arc:
        x += step_x
        y += step_y
        points.append((x * scale_x + origin_x, y * scale_y + origin_y))
    return points


arcs = [decode(arc) for arc in topology["arcs"]]


def outline(ring):
    """Joins a ring's arcs. A negative index is an arc that runs backwards."""
    points = []
    for index in ring:
        arc = arcs[index] if index >= 0 else arcs[~index][::-1]
        points.extend(arc[1:] if points else arc)
    return points


def unwrap(ring):
    """Removes the jumps of land that crosses the 180th meridian, by carrying on past it."""
    points = ring[:1]
    for x, y in ring[1:]:
        last = points[-1][0]
        x += 360 * round((last - x) / 360)
        points.append((x, y))
    # A ring around the South Pole never meets itself, so it closes along the bottom of the map.
    if abs(points[-1][0] - points[0][0]) > 180:
        points += [(points[-1][0], -90), (points[0][0], -90)]
    return points


rings = [unwrap(outline(ring))
         for geometry in topology["objects"]["land"]["geometries"]
         for polygon in geometry["arcs"]
         for ring in polygon]


def crossings(ring, lng, lat):
    count = 0
    for (x1, y1), (x2, y2) in zip(ring, ring[1:] + ring[:1]):
        if (y1 > lat) != (y2 > lat) and lng < (x2 - x1) * (lat - y1) / (y2 - y1) + x1:
            count += 1
    return count


rows = []
lat = -84.0
row = 0
while lat <= 84:
    # Fewer dots towards the poles, where the circles of latitude are shorter.
    count = max(1, round(360 * math.cos(math.radians(lat)) / DOT_SPACING_DEGREES))
    near = [ring for ring in rings if min(y for _, y in ring) <= lat <= max(y for _, y in ring)]
    for i in range(count):
        lng = -180 + (i + (row % 2) / 2) * 360 / count
        hits = sum(crossings(ring, lng + turn, lat) for ring in near for turn in (-360, 0, 360))
        if hits % 2:
            rows.append(f"{lat:.1f},{lng:.1f}")
    lat += DOT_SPACING_DEGREES
    row += 1

with open(target, "w", encoding="ascii") as out:
    out.write("\n".join(rows) + "\n")
print(f"{len(rows)} dots")

#!/usr/bin/env python3
"""Builds app/src/main/assets/places.csv from GeoNames' cities15000.txt.

Usage: build_places.py path/to/cities15000.txt app/src/main/assets/places.csv

Only countries with wide Street View coverage are kept, so few searches come back empty.
"""
import sys

COUNTRIES = set("""
    PT ES FR IT GB IE NL BE LU CH AT DE DK SE NO FI IS PL CZ SK HU RO BG GR HR SI EE LV LT MT
    AD MC LI SM RS ME AL MK TR UA RU
    US CA MX GT CR PA CO EC PE BO CL AR UY BR DO PR
    JP KR TW HK SG MY TH ID PH KH LA LK IN BD MN KG IL JO AE QA OM AU NZ
    ZA BW SZ LS KE UG GH SN NG RW TN
""".split())

source, target = sys.argv[1:3]
rows = []
with open(source, encoding="utf-8") as lines:
    for line in lines:
        fields = line.rstrip("\n").split("\t")
        lat, lng, country = fields[4], fields[5], fields[8]
        if country in COUNTRIES:
            rows.append(f"{country},{float(lat):.3f},{float(lng):.3f}")

with open(target, "w", encoding="ascii") as out:
    out.write("\n".join(sorted(set(rows))) + "\n")
print(f"{len(set(rows))} places, {len({row[:2] for row in rows})} countries")

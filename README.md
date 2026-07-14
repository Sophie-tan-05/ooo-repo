# PRG2104 Assignment 2 — Group 23

**E-commerce Data Analytics with Scala Collections** *(Tier B — AI-Assisted)*

**Dataset:** Sephora Products & Skincare Reviews
(Kaggle #5 — [nadyinky/sephora-products-and-skincare-reviews](https://www.kaggle.com/datasets/nadyinky/sephora-products-and-skincare-reviews))

**Analytical question:** How does a product's category (skincare, makeup, fragrance,
haircare, etc.) affect its price, rating, and review volume on Sephora?
*(verbatim from the header comment in `Main.scala`)*

---

## Roles

| Role | Member | Owns |
|---|---|---|
| Architect | Tan Wen Xi (23093495) | Case-class model, pipeline, data loader, error handling |
| Coder | Tiffany Fam Kar Ying (23052301) | Q1–Q3 implementations, Scala collection-API, output |
| Documenter | Tan Wei Ting (23094709) | README, Section 2 discussion, AI Interaction Log |

---

## Section 1 question targets

- **Q1 — Top-N:** Top 5 categories ranked by total review volume.
- **Q2 — Aggregation by group:** Average price (RM / Malaysian Ringgit) per category.
- **Q3 — Filter + relational pipeline:** Products priced in the top 25% by price
  AND rated below their own category's average rating.

---

## Results at a glance

*(Full output: [`docs/output.txt`](docs/output.txt); Q3 lists all 34 flagged products.)*

**Q1 — Top 5 categories by total review volume**

| Rank | Category | Total reviews |
|---|---|---|
| 1 | Makeup | 326,709 |
| 2 | Skincare | 144,476 |
| 3 | Haircare | 135,671 |
| 4 | Bath & Body | 118,630 |
| 5 | Fragrance | 96,353 |

**Q2 — Average price (RM) per category**

| Category | Avg. price | n |
|---|---|---|
| Bath & Body | RM 145.63 | 58 |
| Fragrance | RM 735.51 | 59 |
| Haircare | RM 161.14 | 49 |
| Makeup | RM 183.16 | 53 |
| Skincare | RM 511.48 | 48 |
| Tools & Brushes | RM 860.31 | 45 |

**Q3 — Top-quartile-price products rated below their category average**

34 products flagged, e.g. `Revlon Tools & Brushes Item 1306` at RM 1876.54
(rating 4.0 vs. category average 4.04) tops the list by price — see
`docs/output.txt` for the complete, ranked list.

---

## Project layout

```
build.sbt
src/main/scala/Main.scala   # data model + Q1-Q3 pipelines
data/sephora_sample.csv     # working sample extracted from the Kaggle dataset
docs/output.txt             # sample run output
docs/polymorphism_note.txt  # subtype polymorphism note (Section 1)
```

> **Note on data:** `data/sephora_sample.csv` is a representative sample (312 rows,
> 6 categories) drawn from the full Kaggle CSV so the repo stays small. Swap in
> the full `product_info.csv` from Kaggle and re-map the column names in
> `DataLoader.parseRow` if the marker wants the full-scale run.

---

## Running

```bash
sbt clean compile   # must exit 0 with zero warnings (-Wunused:all is on)
sbt run              # prints Q1, Q2, Q3 to stdout
```

Expected output is checked into `docs/output.txt` — diff your local run against
it to confirm nothing has drifted.

---

## Design notes

- All rows are modelled with immutable `case class`es (`Product`, `TopCategory`,
  `CategoryPriceStat`, `UnderratedPremiumProduct`) — no raw tuples, no `var`,
  no `mutable.*`.
- `DataLoader.loadProducts` reads the CSV with the third-party
  `com.github.tototoshi %% scala-csv` library (`CSVReader.allWithHeaders()`)
  instead of hand-rolled `String.split(",")`, so quoted fields with embedded
  commas parse correctly. The read is wrapped in `Try` and returns an
  `Either[String, List[Product]]` — no exception ever escapes to `Main`.
- Missing `price_myr` / `rating` cells are parsed with `toDoubleOption` and
  carried as `Option[Double]`, so a blank cell is dropped from an average
  instead of crashing the pipeline.
- Each of Q1–Q3 chains at least three collection operations
  (`groupBy` → `map`/`foldLeft` → `sortBy`/`filter` → …).

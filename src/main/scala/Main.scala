// PRG2104 Assignment 2 - Section 1
// Dataset #5: Sephora Products & Skincare Reviews
// Analytical question: How does a product's category (skincare, makeup, fragrance, haircare, etc.) affect its price, rating, and review volume on Sephora?
// Group 23
// Data model + Q1-Q3 pipelines below.

import scala.util.{Try, Success, Failure, Using}
import com.github.tototoshi.csv._ // third-party CSV library (S1-4)

// --- Data model -------------------------------------------------------
// Raw row straight off the CSV. price/rating are Option because a small
// number of rows in the real Sephora export have blank values.
final case class Product(
                          productId: String,
                          productName: String,
                          brandName: String,
                          category: String,
                          priceMyr: Option[Double],
                          rating: Option[Double],
                          reviews: Int
                        )

// Result rows use named case classes instead of raw tuples (S1-9).
final case class TopCategory(rank: Int, category: String, totalReviews: Int)
final case class CategoryPriceStat(category: String, averagePrice: Double, productCount: Int)
final case class UnderratedPremiumProduct(
                                           productName: String,
                                           category: String,
                                           priceMyr: Double,
                                           rating: Double,
                                           categoryAverageRating: Double
                                         )

object DataLoader:

  // Column names as they appear in the CSV header row.
  private val ProductIdCol = "product_id"
  private val ProductNameCol = "product_name"
  private val BrandNameCol = "brand_name"
  private val CategoryCol = "category"
  private val PriceCol = "price_myr"
  private val RatingCol = "rating"
  private val ReviewsCol = "reviews"

  /** Parses one CSV row (a header -> cell map, as returned by scala-csv's
   * allWithHeaders) into a Product. Returns None - and is dropped by the
   * caller's flatMap - rather than throwing if a required column is
   * missing, keeping loadProducts total (matches the Try/Either IO rule).
   */
  private def parseRow(row: Map[String, String]): Option[Product] =
    for
      productId <- row.get(ProductIdCol).map(_.trim)
      productName <- row.get(ProductNameCol).map(_.trim)
      brandName <- row.get(BrandNameCol).map(_.trim)
      category <- row.get(CategoryCol).map(_.trim)
    yield
      val price = row.get(PriceCol).flatMap(_.trim.toDoubleOption)
      val rating = row.get(RatingCol).flatMap(_.trim.toDoubleOption)
      val reviews = row.get(ReviewsCol).flatMap(_.trim.toIntOption).getOrElse(0)
      Product(
        productId = productId,
        productName = productName,
        brandName = brandName,
        category = category,
        priceMyr = price,
        rating = rating,
        reviews = reviews
      )

  /** Loads the Sephora sample CSV using the third-party scala-csv library
   * (com.github.tototoshi.csv, S1-4) instead of hand-rolled String.split,
   * so quoted fields containing commas are parsed correctly. All IO is
   * wrapped in Try (S1-5) - no naked exception ever reaches the caller;
   * failures are surfaced as a Left with a human-readable message.
   */
  def loadProducts(path: String): Either[String, List[Product]] =
    Try {
      Using.resource(CSVReader.open(path)) { reader =>
        reader.allWithHeaders()
      }
    } match
      case Failure(exception) =>
        Left(s"Could not read '$path': ${exception.getMessage}")
      case Success(rows) =>
        if rows.isEmpty then Left(s"'$path' is empty - expected a header row plus data.")
        else
          val products = rows.flatMap(parseRow) // flatMap drops any unparsable rows
          if products.isEmpty then Left(s"No valid product rows found in '$path'.")
          else Right(products)

object Analytics:

  /** Q1 - Top-N analysis.
   * Top 5 categories ranked by total review volume (an aggregate metric).
   * Chain: groupBy -> map (sum) -> toList -> sortBy -> take -> zipWithIndex.map
   */
  def topCategoriesByReviewVolume(products: Seq[Product]): List[TopCategory] =
    products
      .groupBy(_.category)
      .map { case (category, prods) => category -> prods.map(_.reviews).sum }
      .toList
      .sortBy { case (_, totalReviews) => -totalReviews } // descending
      .take(5)
      .zipWithIndex
      .map { case ((category, totalReviews), idx) =>
        TopCategory(rank = idx + 1, category = category, totalReviews = totalReviews)
      }

  /** Q2 - Aggregation by group.
   * Average price per category. Missing prices are Option-handled (S1-11):
   * a product with no price simply does not contribute to the average.
   * Chain: groupBy -> map (flatMap+foldLeft to aggregate) -> toList -> sortBy
   */
  def averagePricePerCategory(products: List[Product]): List[CategoryPriceStat] =
    products
      .groupBy(_.category)
      .map { case (category, prods) =>
        val prices: List[Double] = prods.flatMap(_.priceMyr) // drops the Nones
        val total: Double = prices.foldLeft(0.0)(_ + _)
        val average = if prices.isEmpty then 0.0 else total / prices.length
        CategoryPriceStat(category = category, averagePrice = average, productCount = prods.length)
      }
      .toList
      .sortBy(stat => stat.category)

  /** Q3 - Filter + relational pipeline.
   * "Products in the top 25% of price AND below their category's average
   * rating" - the two conditions the brief asks for, combined with &&.
   * Chain: sortBy -> (threshold calc) -> filter -> map -> sortBy  (>= 3 ops)
   */
  def underratedPremiumProducts(products: List[Product]): List[UnderratedPremiumProduct] =
    val priced = products.filter(_.priceMyr.isDefined)
    val sortedPrices = priced.flatMap(_.priceMyr).sortBy(identity)
    if sortedPrices.isEmpty then Nil
    else
      val thresholdIndex = ((sortedPrices.length * 0.75).ceil.toInt - 1).max(0)
      val priceTopQuartileThreshold = sortedPrices(thresholdIndex)

      val categoryAverages: Map[String, Double] =
        products
          .groupBy(_.category)
          .map { case (category, prods) =>
            val ratings = prods.flatMap(_.rating)
            val avg = if ratings.isEmpty then 0.0 else ratings.foldLeft(0.0)(_ + _) / ratings.length
            category -> avg
          }

      priced
        .filter(p => p.rating.isDefined) // must have a rating to compare
        .filter(p =>
          val price = p.priceMyr.get
          val rating = p.rating.get
          val categoryAvg = categoryAverages.getOrElse(p.category, 0.0)
          price >= priceTopQuartileThreshold && rating < categoryAvg // two conditions, &&
        )
        .map(p =>
          UnderratedPremiumProduct(
            productName = p.productName,
            category = p.category,
            priceMyr = p.priceMyr.get,
            rating = p.rating.get,
            categoryAverageRating = categoryAverages.getOrElse(p.category, 0.0)
          )
        )
        .sortBy(_.priceMyr)(Ordering[Double].reverse)

object Main:
  def main(args: Array[String]): Unit =
    val csvPath = if args.nonEmpty then args(0) else "data/sephora_sample.csv"

    DataLoader.loadProducts(csvPath) match
      case Left(error) =>
        println(s"[ERROR] $error")
      case Right(products) =>
        println(s"Loaded ${products.length} products from '$csvPath'.\n")

        println("=== Q1: Top 5 categories by total review volume ===")
        Analytics.topCategoriesByReviewVolume(products).foreach { top =>
          println(f"${top.rank}%d. ${top.category}%-16s total reviews = ${top.totalReviews}%,d")
        }

        println("\n=== Q2: Average price (RM) per category ===")
        Analytics.averagePricePerCategory(products).foreach { stat =>
          println(f"${stat.category}%-16s avg price = RM${stat.averagePrice}%7.2f  (n=${stat.productCount})")
        }

        println("\n=== Q3: Top-quartile-price products rated below their category average ===")
        val flagged = Analytics.underratedPremiumProducts(products)
        if flagged.isEmpty then println("(none found)")
        else
          flagged.foreach { p =>
            println(
              f"${p.productName}%-40s [${p.category}%-14s] RM${p.priceMyr}%7.2f  rating=${p.rating}%.1f (cat avg=${p.categoryAverageRating}%.2f)"
            )
          }

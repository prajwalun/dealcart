# Mock Data Catalog for Load Testing

This document describes the realistic mock data catalog used in DealCart+ for 100K request load testing.

## Product Catalog

The `vendor-mock` service includes 50+ realistic products across multiple categories with accurate pricing.

### Electronics ($79 - $1,299)

| Product Keyword | Base Price | Typical Range (±15% vendor variance) |
|----------------|------------|--------------------------------------|
| laptop         | $899       | $764 - $1,034                        |
| macbook        | $1,299     | $1,104 - $1,494                      |
| iphone         | $799       | $679 - $919                          |
| ipad           | $599       | $509 - $689                          |
| airpods        | $199       | $169 - $229                          |
| watch          | $399       | $339 - $459                          |
| monitor        | $349       | $297 - $401                          |
| keyboard       | $129       | $110 - $148                          |
| mouse          | $79        | $67 - $91                            |
| webcam         | $89        | $76 - $102                           |
| speaker        | $149       | $127 - $171                          |
| headphones     | $249       | $212 - $286                          |
| camera         | $899       | $764 - $1,034                        |
| drone          | $1,199     | $1,019 - $1,379                      |
| tablet         | $499       | $424 - $574                          |

### Home & Kitchen ($49 - $249)

| Product Keyword | Base Price | Typical Range |
|----------------|------------|---------------|
| blender        | $79        | $67 - $91     |
| toaster        | $49        | $42 - $56     |
| microwave      | $129       | $110 - $148   |
| vacuum         | $249       | $212 - $286   |
| coffee         | $99        | $84 - $114    |
| airfryer       | $149       | $127 - $171   |
| mixer          | $69        | $59 - $79     |
| kettle         | $59        | $50 - $68     |
| toaster-oven   | $89        | $76 - $102    |

### Sports & Outdoors ($29 - $599)

| Product Keyword | Base Price | Typical Range |
|----------------|------------|---------------|
| bike           | $399       | $339 - $459   |
| yoga-mat       | $29        | $25 - $33     |
| dumbbell       | $49        | $42 - $56     |
| treadmill      | $599       | $509 - $689   |
| tent           | $129       | $110 - $148   |
| backpack       | $79        | $67 - $91     |
| sleeping-bag   | $89        | $76 - $102    |
| hiking-boots   | $149       | $127 - $171   |

### Books & Media ($9.99 - $49.99)

| Product Keyword | Base Price | Typical Range |
|----------------|------------|---------------|
| book           | $19.99     | $17 - $23     |
| textbook       | $49.99     | $42 - $57     |
| ebook          | $9.99      | $8 - $11      |

### Clothing ($29 - $129)

| Product Keyword | Base Price | Typical Range |
|----------------|------------|---------------|
| jacket         | $129       | $110 - $148   |
| shoes          | $89        | $76 - $102    |
| jeans          | $59        | $50 - $68     |
| shirt          | $29        | $25 - $33     |
| hoodie         | $49        | $42 - $56     |

### Toys & Games ($19.99 - $59)

| Product Keyword | Base Price | Typical Range |
|----------------|------------|---------------|
| lego           | $59        | $50 - $68     |
| puzzle         | $19.99     | $17 - $23     |
| boardgame      | $39.99     | $34 - $46     |
| controller     | $59        | $50 - $68     |

### Office Supplies ($29 - $249)

| Product Keyword | Base Price | Typical Range |
|----------------|------------|---------------|
| desk           | $199       | $169 - $229   |
| chair          | $249       | $212 - $286   |
| lamp           | $49        | $42 - $56     |
| organizer      | $29        | $25 - $33     |

### Beauty & Personal Care ($14.99 - $79)

| Product Keyword | Base Price | Typical Range |
|----------------|------------|---------------|
| perfume        | $79        | $67 - $91     |
| shampoo        | $14.99     | $13 - $17     |
| razor          | $29.99     | $25 - $34     |
| trimmer        | $49        | $42 - $56     |

### Unknown Products

For any product not in the catalog, the system generates a price between **$10 - $300** based on the product ID hash.

---

## Inventory Levels (Checkout Service)

The checkout service maintains realistic inventory for load testing:

### High-Demand Electronics
- `sku-iphone`: 10,000 units
- `sku-airpods`: 15,000 units
- `sku-mouse`: 18,000 units
- `sku-keyboard`: 12,000 units
- `sku-watch`: 8,000 units

### Medium-Demand Products
- `sku-laptop`: 5,000 units
- `sku-ipad`: 7,000 units
- `sku-headphones`: 6,000 units
- `sku-tablet`: 5,000 units
- `sku-blender`: 8,000 units

### Limited Stock Items
- `sku-drone`: 1,500 units
- `sku-camera`: 2,000 units
- `sku-macbook`: 3,000 units

### Default Inventory
- **Legacy test SKUs** (`sku-123`, `sku-456`, `sku-789`): 50,000 units each
- **Unknown SKUs**: 100,000 units (default fallback for load testing)

---

## JMeter Load Test Scenarios

### Scenario 1: Popular Electronics Mix
Simulates Black Friday traffic with high-demand electronics:
```
30% - sku-iphone
20% - sku-airpods
15% - sku-laptop
15% - sku-keyboard
10% - sku-mouse
10% - sku-headphones
```

### Scenario 2: Diverse Product Mix
Balanced mix across all categories:
```
20% - Electronics (laptop, iphone, airpods)
20% - Home & Kitchen (blender, coffee, microwave)
20% - Sports & Outdoors (bike, yoga-mat, backpack)
20% - Clothing (jacket, shoes, jeans)
20% - Books & Toys (book, lego, puzzle)
```

### Scenario 3: High-Value Items
Tests system under expensive product load:
```
25% - macbook ($1,299)
25% - drone ($1,199)
25% - camera ($899)
25% - treadmill ($599)
```

### Scenario 4: Budget Items
Tests high-volume, low-value transactions:
```
30% - book ($19.99)
25% - shirt ($29)
20% - puzzle ($19.99)
15% - yoga-mat ($29)
10% - ebook ($9.99)
```

---

## Product ID Formats

The system supports multiple product ID formats:

1. **SKU Format**: `sku-<product-name>`
   - Example: `sku-laptop`, `sku-headphones`
   - Maps directly to inventory

2. **Keyword Format**: `<product-name>`
   - Example: `laptop`, `headphones`
   - Fuzzy matches to catalog (contains check)

3. **Hash-based**: Any unique ID
   - Example: `prod-12345`, `item-abc`
   - Generates price from hash ($10-$300)

---

## Vendor Pricing Variance

Each vendor applies a **±15% variance** to catalog prices to simulate competitive pricing:

**Example: Laptop ($899 base)**
- Amazon (amz): $850 (5% below base)
- Best Buy (bb): $925 (3% above base)

This creates realistic "best price" scenarios for testing.

---

## Latency Simulation

Vendor responses follow a realistic latency distribution:
- **p50**: ~80ms
- **p95**: ~220ms
- **Max**: 500ms (capped)

Uses exponential distribution with 20ms base latency.

---

## Stock Levels for Load Testing

Total available inventory supports **100K+ checkout requests**:

| Category       | Total Units Available |
|----------------|-----------------------|
| Electronics    | ~100,000              |
| Home & Kitchen | ~50,000               |
| Sports         | ~40,000               |
| Clothing       | ~50,000               |
| Books/Toys     | ~50,000               |
| **Total**      | **~290,000 units**    |

**Unknown products default to 100,000 units** to prevent inventory failures during stress tests.

---

## Testing Recommendations

### Quick Smoke Test (1 minute)
```bash
# Test a few popular products
curl "http://localhost/api/search?q=laptop"
curl "http://localhost/api/search?q=headphones"
curl "http://localhost/api/search?q=book"
```

### JMeter Thread Groups
- **Ramp-up**: 5 minutes to 1000 threads
- **Sustained**: 1000 threads for 15 minutes
- **Product mix**: Use CSV data set with 50+ products
- **Think time**: 1-3 seconds between requests

### Expected Results
- **Search latency (p95)**: <500ms
- **Checkout latency (p95)**: <2s
- **Success rate**: >99%
- **Inventory conflicts**: <0.1%

---

## Mock Data Philosophy

This catalog balances:
1. **Realism**: Actual product names and realistic prices
2. **Variety**: 50+ products across 8 categories
3. **Scalability**: 290K+ inventory for 100K test requests
4. **Determinism**: Same product always gets similar price across vendors
5. **Flexibility**: Unknown products still work (hash-based pricing)

Perfect for demos, load testing, and portfolio showcasing! 🚀


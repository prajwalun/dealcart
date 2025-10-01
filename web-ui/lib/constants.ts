export const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || ""

export const MOCK_CUSTOMER_ID = "cust-demo-123"

export const PAYMENT_METHODS = [
  { id: "pm-card-123", label: "Credit Card ****1234" },
  { id: "pm-paypal-456", label: "PayPal" },
  { id: "pm-apple-789", label: "Apple Pay" },
]

export const VENDOR_NAMES: Record<string, string> = {
  amz: "Amazon",
  bb: "Best Buy",
  wmt: "Walmart",
  tgt: "Target",
}

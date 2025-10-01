export type Money = {
  amount_cents: number
  currency_code: string
}

export type Quote = {
  vendor: string
  vendorId: string
  price: number // dollars (e.g., 129.99)
  currency: string
  estimatedDays: number
  timestamp: number
}

export type NodeState =
  | "NODE_STATE_PENDING"
  | "NODE_STATE_RUNNING"
  | "NODE_STATE_COMPLETED"
  | "NODE_STATE_FAILED"
  | "NODE_STATE_SKIPPED"

export type NodeStatus = {
  nodeId: "reserve" | "price" | "tax" | "pay" | "confirm" | "release" | "void"
  state: NodeState
  message: string
  timestamp: number
  errorCode?: string
  errorMessage?: string
}

export type CheckoutItem = {
  productId: string
  quantity: number
  unitPrice: {
    currencyCode: string
    amountCents: number
  }
  vendorId: string
}

export type CheckoutRequest = {
  customerId: string
  items: CheckoutItem[]
  shippingAddress: string
  paymentMethodId: string
}

export type CheckoutResponse = {
  checkoutId: string
  status: string
  message: string
  totalAmount?: number
  currency?: string
}

export type CartItem = {
  productId: string
  productName: string
  quantity: number
  unitPrice: number
  currency: string
  vendorId: string
  vendor: string
  estimatedDays: number
}

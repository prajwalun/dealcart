# DealCart+ UI Integration Spec

## API Base URL

```typescript
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || '';
// Local dev: http://localhost (or empty for same-origin)
// Production: https://your-ec2-domain.com
```

---

## TypeScript Types (Required)

```typescript
export type Money = {
  amount_cents: number;
  currency_code: string;
};

export type Quote = {
  vendor: string;
  vendorId: string;
  price: number;        // dollars (e.g., 129.99)
  currency: string;
  estimatedDays: number;
  timestamp: number;
};

export type NodeState = "NODE_STATE_PENDING" | "NODE_STATE_RUNNING" | "NODE_STATE_COMPLETED" | "NODE_STATE_FAILED" | "NODE_STATE_SKIPPED";

export type NodeStatus = {
  nodeId: "reserve" | "price" | "tax" | "pay" | "confirm" | "release" | "void";
  state: NodeState;
  message: string;
  timestamp: number;
  errorCode?: string;
  errorMessage?: string;
};

export type CheckoutItem = {
  productId: string;
  quantity: number;
  unitPrice: {
    currencyCode: string;
    amountCents: number;
  };
  vendorId: string;
};

export type CheckoutRequest = {
  customerId: string;
  items: CheckoutItem[];
  shippingAddress: string;
  paymentMethodId: string;
};

export type CheckoutResponse = {
  checkoutId: string;
  status: string;
  message: string;
  totalAmount?: number;
  currency?: string;
};
```

---

## API Endpoints

### 1. Search Products (SSE Stream)

**Endpoint**: `GET /api/search?q={query}`

**Response**: `text/event-stream`

**Event Format**:
```
event: quote
data: {"vendor":"amz","vendorId":"amz","price":129.99,"currency":"USD","estimatedDays":3,"timestamp":1696118400000}

event: quote
data: {"vendor":"bb","vendorId":"bb","price":134.99,"currency":"USD","estimatedDays":5,"timestamp":1696118400123}
```

**Notes**:
- Server sends `heartbeat` comments every 15s (client should ignore)
- Stream closes when all vendors respond
- Deduplicate by `vendorId` if you see duplicates

**Client Code**:
```typescript
const eventSource = new EventSource(`${API_BASE}/api/search?q=${encodeURIComponent(query)}`);

eventSource.addEventListener('quote', (e) => {
  const quote: Quote = JSON.parse(e.data);
  // Add to quotes list
});

eventSource.onerror = () => {
  eventSource.close();
};

// Close on cleanup
return () => eventSource.close();
```

---

### 2. Get Best Quote (Non-Streaming, for Quick Fetch)

**Endpoint**: `GET /api/quote?productId={productId}&mode=best`

**Response**: `application/json`

```json
{
  "vendor": "amz",
  "vendorId": "amz",
  "price": 129.99,
  "currency": "USD",
  "estimatedDays": 3,
  "timestamp": 1696118400000
}
```

**Notes**:
- `mode=best` returns single cheapest quote (default)
- `mode=all` returns array of all quotes
- Use this for "Get Best Quote" button

---

### 3. Start Checkout

**Endpoint**: `POST /api/checkout`

**Headers**:
```
Content-Type: application/json
Idempotency-Key: {unique-uuid}
```

**Request Body**:
```json
{
  "customerId": "cust-123",
  "items": [
    {
      "productId": "sku-123",
      "quantity": 2,
      "unitPrice": {
        "currencyCode": "USD",
        "amountCents": 12999
      },
      "vendorId": "amz"
    }
  ],
  "shippingAddress": "123 Main St, City, State 12345",
  "paymentMethodId": "pm-card-123"
}
```

**Response**:
```json
{
  "checkoutId": "checkout-1696118400-1",
  "status": "CHECKOUT_STATUS_PENDING",
  "message": "Checkout initiated successfully"
}
```

---

### 4. Stream Checkout Status (SSE)

**Endpoint**: `GET /api/checkout/{checkoutId}/stream`

**Response**: `text/event-stream`

**Event Format**:
```
event: status
data: {"nodeId":"reserve","state":"NODE_STATE_RUNNING","message":"Reserving inventory","timestamp":1696118400000}

event: status
data: {"nodeId":"reserve","state":"NODE_STATE_COMPLETED","message":"Inventory reserved successfully","timestamp":1696118400123}

event: status
data: {"nodeId":"price","state":"NODE_STATE_RUNNING","message":"Calculating price","timestamp":1696118400150}
```

**DAG Nodes** (in order):
1. `reserve` - Inventory reservation
2. `price` - Price calculation (parallel with tax)
3. `tax` - Tax calculation (parallel with price)
4. `pay` - Payment processing
5. `confirm` - Order confirmation

**Rollback Nodes** (on failure):
- `void` - Void payment (if payment succeeded but later failed)
- `release` - Release inventory reservation

**Client Code**:
```typescript
const eventSource = new EventSource(`${API_BASE}/api/checkout/${checkoutId}/stream`);

eventSource.addEventListener('status', (e) => {
  const status: NodeStatus = JSON.parse(e.data);
  // Update timeline UI
});

eventSource.onerror = () => {
  eventSource.close();
};
```

---

## UI Components & Flow

### Page 1: Product Search

**Search Input**:
- Text input with debouncing (300ms)
- On change: close old EventSource, open new one to `/api/search?q={query}`

**Quote Table**:
- Columns: Vendor, Price, Delivery, Action
- Stream quotes as they arrive
- Highlight cheapest (badge: "Best Price")
- Each row has "Add to Cart" button

**"Get Best Quote" Button** (Alternative):
- Calls `/api/quote?productId={id}&mode=best`
- Instantly adds cheapest quote to cart
- Faster UX than waiting for stream

### Page 2: Shopping Cart

**Cart Panel**:
- List of selected items (vendor, product, quantity, unit price, subtotal)
- Edit quantity or remove items
- Show total with tax estimate
- "Checkout" button

**Checkout Form**:
- Customer ID input
- Shipping address textarea
- Payment method dropdown (mock values: "pm-card-123", "pm-paypal-456")
- Generate `Idempotency-Key` with `crypto.randomUUID()`

### Page 3: Checkout Status

**Timeline Component**:
- Shows DAG nodes vertically: Reserve → Price/Tax → Pay → Confirm
- Each node shows:
  - Icon (spinner for RUNNING, checkmark for COMPLETED, X for FAILED)
  - Name and message
  - Duration badge
  - State badge (color-coded)

**States**:
- `NODE_STATE_PENDING`: Gray, "Queued"
- `NODE_STATE_RUNNING`: Blue spinner, "In Progress"
- `NODE_STATE_COMPLETED`: Green checkmark, "Done"
- `NODE_STATE_FAILED`: Red X, "Failed"
- `NODE_STATE_SKIPPED`: Yellow, "Skipped"

**Rollback Notice**:
- If you see `release` or `void` nodes, show warning banner:
  - "Checkout failed. Payment voided and inventory released."

**Final Status**:
- When stream closes, show success or failure message
- Success: "Order confirmed! Checkout ID: {checkoutId}"
- Failure: "Checkout failed. Please try again."

---

## Technical Constraints

### SSE Handling

```typescript
// Always close EventSource on unmount
useEffect(() => {
  const es = new EventSource(url);
  return () => es.close();
}, [url]);
```

### Error Handling

- Network errors: Show toast "Connection lost. Retrying..."
- 429 Rate Limit: Show toast "Too many requests. Please wait."
- 5xx errors: Show toast "Server error. Please try again."

### Loading States

- Show skeleton loaders while waiting for first quote
- Disable checkout button while processing
- Show spinner on timeline during checkout

### Accessibility

- Semantic HTML (`<table>`, `<form>`, `<button>`)
- ARIA labels on interactive elements
- Keyboard navigation support
- Focus management on modals/errors

### Responsive Design

- Mobile: Single column, collapsible cart
- Tablet: Two columns (search + cart)
- Desktop: Three columns with timeline sidebar

---

## Environment Variables

```env
# .env.local
NEXT_PUBLIC_API_BASE_URL=http://localhost
```

For production, set to your EC2 domain.

---

## Example Flow

1. User types "headphones" → SSE streams 2 quotes from amz and bb
2. User clicks "Add to Cart" on cheapest → added to cart
3. User clicks "Checkout" → fills form → submits
4. POST returns checkoutId → redirect to status page
5. Status page opens SSE stream → shows real-time DAG progress
6. Nodes complete: reserve ✓ → price ✓ / tax ✓ → pay ✓ → confirm ✓
7. Success message shown, order ID displayed

---

## Mock Data (for Development)

```typescript
// Customer IDs
const MOCK_CUSTOMER_ID = "cust-demo-123";

// Payment Methods
const PAYMENT_METHODS = [
  { id: "pm-card-123", label: "Credit Card ****1234" },
  { id: "pm-paypal-456", label: "PayPal" },
  { id: "pm-apple-789", label: "Apple Pay" }
];

// Product IDs are generated from search queries
// Example: "headphones" → "sku-432" (deterministic hash)
```

---

## Styling Guidance

**Theme**:
- Modern, clean, professional
- Primary color: Blue (#3B82F6)
- Success: Green (#10B981)
- Error: Red (#EF4444)
- Warning: Yellow (#F59E0B)

**Components**:
- Cards with subtle shadows
- Rounded corners (rounded-lg)
- Smooth transitions
- Hover states on interactive elements

**Typography**:
- Headings: Bold, large
- Body: Regular, readable (text-base)
- Monospace for IDs (checkout-123)

---

## Error Scenarios to Handle

1. **No quotes returned**: Show "No vendors available. Try another product."
2. **Checkout fails**: Display error message from API
3. **Stream disconnects**: Retry connection or show error
4. **Rate limit hit**: Show friendly "Please slow down" message

---

## Testing Checklist for v0

- [ ] Search shows loading spinner
- [ ] Quotes stream in real-time (table updates)
- [ ] Best quote is highlighted
- [ ] Cart shows correct subtotals
- [ ] Checkout form validates required fields
- [ ] Checkout status timeline updates live
- [ ] Success/failure states display correctly
- [ ] Mobile layout is usable
- [ ] Error toasts appear on failures

---

## Notes

- The backend handles CORS, so no client-side config needed
- Request IDs are automatically added by the gateway (check `X-Request-ID` header for debugging)
- All prices are in cents to avoid floating-point issues
- Idempotency keys prevent duplicate checkouts on retry


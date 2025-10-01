"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { Trash2, ShoppingBag, CreditCard, Package, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { useCartStore } from "@/lib/cart-store"
import { MOCK_CUSTOMER_ID, PAYMENT_METHODS, API_BASE, VENDOR_NAMES } from "@/lib/constants"
import type { CheckoutRequest, CheckoutResponse } from "@/lib/types"
import { useToast } from "@/hooks/use-toast"

export default function CartPage() {
  const { items, removeItem, updateQuantity, clearCart, getTotalAmount } = useCartStore()
  const [customerId, setCustomerId] = useState(MOCK_CUSTOMER_ID)
  const [shippingAddress, setShippingAddress] = useState("")
  const [paymentMethodId, setPaymentMethodId] = useState("")
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [errors, setErrors] = useState<{ address?: string; payment?: string }>({})
  const { toast } = useToast()
  const router = useRouter()

  const totalAmount = getTotalAmount()
  const estimatedTax = totalAmount * 0.08 // 8% tax estimate
  const grandTotal = totalAmount + estimatedTax

  const handleCheckout = async () => {
    const newErrors: { address?: string; payment?: string } = {}

    if (!shippingAddress.trim()) {
      newErrors.address = "Shipping address is required"
    }

    if (!paymentMethodId) {
      newErrors.payment = "Payment method is required"
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors)
      toast({
        title: "Missing Information",
        description: "Please fill in all required fields",
        variant: "destructive",
      })
      return
    }

    setErrors({})
    setIsSubmitting(true)

    const checkoutRequest: CheckoutRequest = {
      customerId,
      items: items.map((item) => ({
        productId: item.productId,
        quantity: item.quantity,
        unitPrice: {
          currencyCode: item.currency,
          amountCents: Math.round(item.unitPrice * 100),
        },
        vendorId: item.vendorId,
      })),
      shippingAddress,
      paymentMethodId,
    }

    try {
      const response = await fetch(`${API_BASE}/api/checkout`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify(checkoutRequest),
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
      }

      const data: CheckoutResponse = await response.json()

      toast({
        title: "Checkout Initiated",
        description: `Order ID: ${data.checkoutId}`,
      })

      clearCart()
      router.push(`/checkout/${data.checkoutId}`)
    } catch (error) {
      console.error("[v0] Checkout error:", error)
      toast({
        title: "Checkout Failed",
        description: "Unable to process checkout. Please try again.",
        variant: "destructive",
      })
    } finally {
      setIsSubmitting(false)
    }
  }

  if (items.length === 0) {
    return (
      <div className="container py-16">
        <Card className="max-w-md mx-auto text-center shadow-lg">
          <CardHeader className="pt-12">
            <div className="h-24 w-24 rounded-full bg-muted/50 flex items-center justify-center mx-auto mb-6">
              <ShoppingBag className="h-12 w-12 text-muted-foreground opacity-50" />
            </div>
            <CardTitle className="text-2xl">Your cart is empty</CardTitle>
            <CardDescription className="text-base mt-2">Start shopping to add items to your cart</CardDescription>
          </CardHeader>
          <CardFooter className="justify-center pb-12">
            <Button size="lg" onClick={() => router.push("/")} className="px-8">
              Browse Products
            </Button>
          </CardFooter>
        </Card>
      </div>
    )
  }

  return (
    <div className="container py-8">
      <div className="grid lg:grid-cols-3 gap-8">
        {/* Cart Items */}
        <div className="lg:col-span-2 space-y-4">
          <Card className="shadow-md">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-xl">
                <ShoppingBag className="h-6 w-6 text-primary" />
                Shopping Cart ({items.length} {items.length === 1 ? "item" : "items"})
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {items.map((item, index) => (
                <div
                  key={`${item.productId}-${item.vendorId}`}
                  className="flex items-start gap-4 p-4 rounded-lg border transition-all duration-200 hover:shadow-md animate-in fade-in slide-in-from-left-4"
                  style={{ animationDelay: `${index * 50}ms` }}
                >
                  <div className="h-12 w-12 rounded-lg bg-primary/10 flex items-center justify-center font-bold text-primary flex-shrink-0">
                    {item.productName.charAt(0)}
                  </div>
                  <div className="flex-1 space-y-2">
                    <h3 className="font-semibold text-lg text-foreground">{item.productName}</h3>
                    <p className="text-sm text-muted-foreground">
                      Vendor: <span className="font-medium">{VENDOR_NAMES[item.vendorId] || item.vendor}</span>
                    </p>
                    <div className="flex items-center gap-2 text-sm text-muted-foreground">
                      <Package className="h-4 w-4" />
                      Delivery: {item.estimatedDays} {item.estimatedDays === 1 ? "day" : "days"}
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <Input
                      type="number"
                      min="1"
                      value={item.quantity}
                      onChange={(e) =>
                        updateQuantity(item.productId, item.vendorId, Number.parseInt(e.target.value) || 1)
                      }
                      className="w-20 text-center"
                    />
                    <div className="text-right min-w-28">
                      <p className="font-bold text-lg text-foreground">
                        ${(item.unitPrice * item.quantity).toFixed(2)}
                      </p>
                      <p className="text-xs text-muted-foreground">${item.unitPrice.toFixed(2)} each</p>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => removeItem(item.productId, item.vendorId)}
                      className="hover:bg-destructive/10 transition-colors"
                    >
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <Card className="shadow-lg lg:sticky lg:top-20">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-xl">
                <CreditCard className="h-6 w-6 text-primary" />
                Checkout
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="space-y-2">
                <Label htmlFor="customerId" className="text-base">
                  Customer ID
                </Label>
                <Input
                  id="customerId"
                  value={customerId}
                  onChange={(e) => setCustomerId(e.target.value)}
                  placeholder="cust-123"
                  className="h-11"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="shippingAddress" className="text-base">
                  Shipping Address *
                </Label>
                <Textarea
                  id="shippingAddress"
                  value={shippingAddress}
                  onChange={(e) => {
                    setShippingAddress(e.target.value)
                    if (errors.address) setErrors((prev) => ({ ...prev, address: undefined }))
                  }}
                  placeholder="123 Main St, City, State 12345"
                  rows={3}
                  className={errors.address ? "border-destructive focus:ring-destructive" : ""}
                />
                {errors.address && <p className="text-sm text-destructive">{errors.address}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="paymentMethod" className="text-base">
                  Payment Method *
                </Label>
                <Select
                  value={paymentMethodId}
                  onValueChange={(value) => {
                    setPaymentMethodId(value)
                    if (errors.payment) setErrors((prev) => ({ ...prev, payment: undefined }))
                  }}
                >
                  <SelectTrigger
                    id="paymentMethod"
                    className={`h-11 ${errors.payment ? "border-destructive focus:ring-destructive" : ""}`}
                  >
                    <SelectValue placeholder="Select payment method" />
                  </SelectTrigger>
                  <SelectContent>
                    {PAYMENT_METHODS.map((method) => (
                      <SelectItem key={method.id} value={method.id}>
                        {method.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {errors.payment && <p className="text-sm text-destructive">{errors.payment}</p>}
              </div>

              <Separator />

              <div className="space-y-3">
                <div className="flex justify-between text-base">
                  <span className="text-muted-foreground">Subtotal</span>
                  <span className="font-semibold text-foreground">${totalAmount.toFixed(2)}</span>
                </div>
                <div className="flex justify-between text-base">
                  <span className="text-muted-foreground">Estimated Tax (8%)</span>
                  <span className="font-semibold text-foreground">${estimatedTax.toFixed(2)}</span>
                </div>
                <Separator />
                <div className="flex justify-between text-xl font-bold">
                  <span className="text-foreground">Total</span>
                  <span className="text-primary">${grandTotal.toFixed(2)}</span>
                </div>
              </div>
            </CardContent>
            <CardFooter>
              <Button
                className="w-full h-12 text-base transition-all duration-200 hover:scale-[1.02] hover:shadow-md"
                size="lg"
                onClick={handleCheckout}
                disabled={isSubmitting}
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="h-5 w-5 mr-2 animate-spin" />
                    Processing...
                  </>
                ) : (
                  "Complete Checkout"
                )}
              </Button>
            </CardFooter>
          </Card>
        </div>
      </div>
    </div>
  )
}

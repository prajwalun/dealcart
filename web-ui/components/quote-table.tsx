"use client"

import { useMemo, useState } from "react"
import { ShoppingCart, Package, Clock, DollarSign, CheckCircle2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useCartStore } from "@/lib/cart-store"
import { VENDOR_NAMES } from "@/lib/constants"
import type { Quote } from "@/lib/types"
import { useToast } from "@/hooks/use-toast"
import { useRouter } from "next/navigation"

interface QuoteTableProps {
  quotes: Quote[]
  isLoading: boolean
  isStreaming: boolean
  searchQuery: string
}

export function QuoteTable({ quotes, isLoading, isStreaming, searchQuery }: QuoteTableProps) {
  const addItem = useCartStore((state) => state.addItem)
  const { toast } = useToast()
  const router = useRouter()
  const [addedItems, setAddedItems] = useState<Set<string>>(new Set())

  const bestQuote = useMemo(() => {
    if (quotes.length === 0) return null
    return quotes.reduce((best, current) => (current.price < best.price ? current : best))
  }, [quotes])

  const handleAddToCart = (quote: Quote) => {
    addItem({
      productId: `sku-${searchQuery.toLowerCase().replace(/\s+/g, "-")}`,
      productName: searchQuery,
      quantity: 1,
      unitPrice: quote.price,
      currency: quote.currency,
      vendorId: quote.vendorId,
      vendor: quote.vendor,
      estimatedDays: quote.estimatedDays,
    })

    setAddedItems((prev) => new Set(prev).add(quote.vendorId))
    setTimeout(() => {
      setAddedItems((prev) => {
        const next = new Set(prev)
        next.delete(quote.vendorId)
        return next
      })
    }, 2000)

    toast({
      title: "Added to cart",
      description: `${searchQuery} from ${VENDOR_NAMES[quote.vendorId] || quote.vendor}`,
    })
  }

  const handleBuyNow = (quote: Quote) => {
    handleAddToCart(quote)
    router.push("/cart")
  }

  if (isLoading) {
    return (
      <Card className="shadow-md">
        <CardHeader>
          <Skeleton className="h-6 w-32" />
          <Skeleton className="h-4 w-48" />
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div key={i} className="flex items-center gap-4 animate-pulse">
                <Skeleton className="h-16 w-full" />
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="shadow-md">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-xl">
              <Package className="h-6 w-6 text-primary" />
              Available Quotes
            </CardTitle>
            <CardDescription className="text-base">
              {isStreaming
                ? "Fetching quotes from vendors..."
                : `Found ${quotes.length} ${quotes.length === 1 ? "quote" : "quotes"}`}
            </CardDescription>
          </div>
          {isStreaming && (
            <Badge variant="secondary" className="gap-2 px-3 py-1.5">
              <span className="relative flex h-2.5 w-2.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-primary"></span>
              </span>
              <span className="font-medium">Live</span>
            </Badge>
          )}
        </div>
      </CardHeader>
      <CardContent>
        {quotes.length === 0 && !isStreaming ? (
          <div className="text-center py-16 text-muted-foreground">
            <Package className="h-16 w-16 mx-auto mb-4 opacity-30" />
            <p className="text-lg font-medium">No quotes available</p>
            <p className="text-sm mt-1">Try a different search term</p>
          </div>
        ) : (
          <div className="rounded-lg border overflow-hidden">
            <Table>
              <TableHeader>
                <TableRow className="bg-muted/50">
                  <TableHead className="font-semibold">Vendor</TableHead>
                  <TableHead className="font-semibold">Price</TableHead>
                  <TableHead className="font-semibold">Delivery</TableHead>
                  <TableHead className="text-right font-semibold">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {quotes.map((quote, index) => {
                  const isBest = bestQuote?.vendorId === quote.vendorId
                  const isAdded = addedItems.has(quote.vendorId)
                  return (
                    <TableRow
                      key={quote.vendorId}
                      className={`transition-all duration-200 animate-in fade-in slide-in-from-bottom-2 ${
                        isBest ? "bg-success/5 border-l-4 border-l-success" : "hover:bg-muted/30"
                      }`}
                      style={{ animationDelay: `${index * 100}ms` }}
                    >
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <div className="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center font-bold text-primary">
                            {(VENDOR_NAMES[quote.vendorId] || quote.vendor).charAt(0)}
                          </div>
                          <div>
                            <div className="font-semibold">{VENDOR_NAMES[quote.vendorId] || quote.vendor}</div>
                            {isBest && (
                              <Badge variant="default" className="bg-success text-white mt-1 text-xs">
                                Best Price
                              </Badge>
                            )}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1.5">
                          <DollarSign className="h-5 w-5 text-muted-foreground" />
                          <span className="text-xl font-bold">{quote.price.toFixed(2)}</span>
                          <span className="text-sm text-muted-foreground">{quote.currency}</span>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2 text-muted-foreground">
                          <Clock className="h-4 w-4" />
                          <span className="font-medium">
                            {quote.estimatedDays} {quote.estimatedDays === 1 ? "day" : "days"}
                          </span>
                        </div>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleAddToCart(quote)}
                            disabled={isAdded}
                            className="transition-all duration-200 hover:scale-105"
                          >
                            {isAdded ? (
                              <>
                                <CheckCircle2 className="h-4 w-4 mr-1 text-success" />
                                Added
                              </>
                            ) : (
                              <>
                                <ShoppingCart className="h-4 w-4 mr-1" />
                                Add to Cart
                              </>
                            )}
                          </Button>
                          <Button
                            variant="default"
                            size="sm"
                            onClick={() => handleBuyNow(quote)}
                            className="transition-all duration-200 hover:scale-105 hover:shadow-md"
                          >
                            Buy Now
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

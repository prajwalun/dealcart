"use client"

import { useState, useEffect, useRef } from "react"
import { Search, TrendingUp, Zap } from "lucide-react"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { QuoteTable } from "@/components/quote-table"
import { API_BASE } from "@/lib/constants"
import type { Quote } from "@/lib/types"
import { useToast } from "@/hooks/use-toast"

export default function SearchPage() {
  const [query, setQuery] = useState("")
  const [quotes, setQuotes] = useState<Quote[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [isStreaming, setIsStreaming] = useState(false)
  const eventSourceRef = useRef<EventSource | null>(null)
  const timeoutRef = useRef<NodeJS.Timeout | null>(null)
  const { toast } = useToast()

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
      }
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }
    }
  }, [])

  // Debounced search
  useEffect(() => {
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current)
    }

    if (!query.trim()) {
      setQuotes([])
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    timeoutRef.current = setTimeout(() => {
      startSearch(query)
    }, 300)

    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current)
      }
    }
  }, [query])

  const startSearch = (searchQuery: string) => {
    // Close existing connection
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
    }

    setQuotes([])
    setIsStreaming(true)
    setIsLoading(false)

    const url = `${API_BASE}/api/search?q=${encodeURIComponent(searchQuery)}`
    const eventSource = new EventSource(url)
    eventSourceRef.current = eventSource

    eventSource.addEventListener("quote", (e) => {
      try {
        const quote: Quote = JSON.parse(e.data)
        setQuotes((prev) => {
          // Deduplicate by vendorId
          const exists = prev.some((q) => q.vendorId === quote.vendorId)
          if (exists) return prev
          return [...prev, quote]
        })
      } catch (error) {
        console.error("[v0] Failed to parse quote:", error)
      }
    })

    eventSource.onerror = (error) => {
      console.error("[v0] EventSource error:", error)
      eventSource.close()
      setIsStreaming(false)
      
      // Only show error if we got zero quotes (real failure)
      // Stream naturally closes after sending quotes, triggering onerror
      setTimeout(() => {
        if (quotes.length === 0) {
          toast({
            title: "Connection Error",
            description: "Failed to fetch quotes. Please try again.",
            variant: "destructive",
          })
        }
      }, 100)
    }

    eventSource.addEventListener("close", () => {
      eventSource.close()
      setIsStreaming(false)
    })
  }

  return (
    <div className="min-h-screen">
      {/* Hero Section */}
      <div className="relative bg-gradient-to-br from-primary via-primary to-blue-600 text-primary-foreground overflow-hidden">
        <div className="absolute inset-0 bg-grid-white/[0.05] bg-[size:20px_20px]" />
        <div className="container relative py-20 text-center space-y-6">
          <h1 className="text-5xl sm:text-6xl font-bold tracking-tight text-balance animate-in fade-in slide-in-from-bottom-4 duration-700">
            Find the Best Deals
            <span className="block mt-2">Across All Vendors</span>
          </h1>
          <p className="text-xl opacity-90 max-w-2xl mx-auto text-pretty animate-in fade-in slide-in-from-bottom-4 duration-500 delay-100">
            Compare prices in real-time from multiple vendors and save on every purchase
          </p>
        </div>
      </div>

      <div className="container mx-auto py-8 space-y-8 max-w-7xl">
        {/* Search Section */}
        <Card className="max-w-3xl mx-auto shadow-lg hover:shadow-xl transition-all duration-200 -mt-12 relative z-10 border-2">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-xl">
              <Search className="h-6 w-6 text-primary" />
              Search Products
            </CardTitle>
            <CardDescription className="text-base">
              Enter a product name to see live quotes from multiple vendors
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="relative">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 h-5 w-5 text-muted-foreground" />
              <Input
                type="text"
                placeholder="Try 'headphones', 'laptop', or 'camera'..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="pl-12 h-14 text-lg transition-all duration-200 focus:ring-2 focus:ring-primary/20"
              />
            </div>
          </CardContent>
        </Card>

        {/* Results Section */}
        {(isLoading || isStreaming || quotes.length > 0) && (
          <div className="max-w-6xl mx-auto animate-in fade-in slide-in-from-bottom-4 duration-500">
            <QuoteTable quotes={quotes} isLoading={isLoading} isStreaming={isStreaming} searchQuery={query} />
          </div>
        )}

        {/* Features Section */}
        {!query && (
          <div className="grid sm:grid-cols-3 gap-6 max-w-4xl mx-auto pt-8">
            <Card className="transition-all duration-200 hover:shadow-lg hover:-translate-y-1 border-2">
              <CardHeader>
                <div className="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center mb-3">
                  <Zap className="h-6 w-6 text-primary" />
                </div>
                <CardTitle className="text-lg">Real-Time Quotes</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  Get live price updates as vendors respond to your search
                </p>
              </CardContent>
            </Card>
            <Card className="transition-all duration-200 hover:shadow-lg hover:-translate-y-1 border-2">
              <CardHeader>
                <div className="h-12 w-12 rounded-full bg-success/10 flex items-center justify-center mb-3">
                  <TrendingUp className="h-6 w-6 text-success" />
                </div>
                <CardTitle className="text-lg">Best Price Guarantee</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  Automatically highlights the cheapest option for you
                </p>
              </CardContent>
            </Card>
            <Card className="transition-all duration-200 hover:shadow-lg hover:-translate-y-1 border-2">
              <CardHeader>
                <div className="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center mb-3">
                  <Search className="h-6 w-6 text-primary" />
                </div>
                <CardTitle className="text-lg">Multi-Vendor Search</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  Compare prices from Amazon, Best Buy, and more in seconds
                </p>
              </CardContent>
            </Card>
          </div>
        )}
      </div>
    </div>
  )
}

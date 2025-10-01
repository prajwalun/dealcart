"use client"

import Link from "next/link"
import { ShoppingCart } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { useCartStore } from "@/lib/cart-store"

export function Header() {
  const items = useCartStore((state) => state.items)
  const itemCount = items.reduce((sum, item) => sum + item.quantity, 0)

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60 shadow-sm">
      <div className="container flex h-16 items-center justify-between">
        <Link href="/" className="flex items-center gap-2 transition-transform duration-200 hover:scale-105">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary shadow-sm">
            <span className="text-lg font-bold text-primary-foreground">D+</span>
          </div>
          <span className="text-xl font-bold text-foreground">DealCart+</span>
        </Link>

        <nav className="flex items-center gap-6">
          <Link
            href="/"
            className="text-sm font-medium text-foreground hover:text-primary transition-colors duration-200"
          >
            Search
          </Link>
          <Link href="/cart" className="relative">
            <Button
              variant="outline"
              size="sm"
              className="gap-2 bg-transparent transition-all duration-200 hover:scale-105 hover:shadow-md"
            >
              <ShoppingCart className="h-4 w-4" />
              <span className="hidden sm:inline">Cart</span>
              {itemCount > 0 && (
                <Badge
                  variant="default"
                  className="ml-1 h-5 min-w-5 px-1.5 animate-in zoom-in-50 bg-primary text-primary-foreground"
                >
                  {itemCount}
                </Badge>
              )}
            </Button>
          </Link>
        </nav>
      </div>
    </header>
  )
}

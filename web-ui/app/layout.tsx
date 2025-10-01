import type React from "react"
import type { Metadata } from "next"
import { GeistSans } from "geist/font/sans"
import { GeistMono } from "geist/font/mono"
import { Analytics } from "@vercel/analytics/next"
import { Toaster } from "@/components/ui/toaster"
import { Header } from "@/components/header"
import { Suspense } from "react"
import "./globals.css"

export const metadata: Metadata = {
  title: "DealCart+ - Compare Prices Across Vendors",
  description: "Find the best deals from multiple vendors in real-time",
  generator: "v0.app",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en">
      <body className={`font-sans ${GeistSans.variable} ${GeistMono.variable}`}>
        <Suspense fallback={<div>Loading...</div>}>
          <Header />
          <main className="min-h-[calc(100vh-4rem)]">{children}</main>
          <Toaster />
          <Analytics />
        </Suspense>
      </body>
    </html>
  )
}

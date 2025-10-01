"use client"

import { useEffect, useState, useRef } from "react"
import { useParams, useRouter } from "next/navigation"
import { ArrowLeft, Copy, CheckCircle2, XCircle, Loader2, Clock, Package } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { API_BASE } from "@/lib/constants"
import type { CheckoutResponse, NodeState } from "@/lib/types"
import { useToast } from "@/hooks/use-toast"

export default function CheckoutStatusPage() {
  const params = useParams()
  const router = useRouter()
  const { toast } = useToast()
  const checkoutId = params.id as string
  const [status, setStatus] = useState<CheckoutResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const eventSourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!checkoutId) return

    const url = `${API_BASE}/api/checkout/${checkoutId}/stream`
    const eventSource = new EventSource(url)
    eventSourceRef.current = eventSource

    eventSource.addEventListener("update", (e) => {
      try {
        const data: CheckoutResponse = JSON.parse(e.data)
        setStatus(data)
        setIsLoading(false)
      } catch (error) {
        console.error("[v0] Failed to parse checkout update:", error)
      }
    })

    eventSource.onerror = (error) => {
      console.error("[v0] EventSource error:", error)
      eventSource.close()
      setIsLoading(false)
      toast({
        title: "Connection Error",
        description: "Lost connection to checkout status. Please refresh.",
        variant: "destructive",
      })
    }

    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close()
      }
    }
  }, [checkoutId, toast])

  const copyToClipboard = () => {
    navigator.clipboard.writeText(checkoutId)
    toast({
      title: "Copied!",
      description: "Checkout ID copied to clipboard",
    })
  }

  const getNodeIcon = (state: NodeState) => {
    switch (state) {
      case "COMPLETED":
        return <CheckCircle2 className="h-6 w-6 text-success" />
      case "FAILED":
        return <XCircle className="h-6 w-6 text-destructive" />
      case "RUNNING":
        return <Loader2 className="h-6 w-6 text-primary animate-spin" />
      default:
        return <Clock className="h-6 w-6 text-muted-foreground" />
    }
  }

  const getNodeBadgeVariant = (state: NodeState) => {
    switch (state) {
      case "COMPLETED":
        return "default"
      case "FAILED":
        return "destructive"
      case "RUNNING":
        return "secondary"
      default:
        return "outline"
    }
  }

  if (isLoading) {
    return (
      <div className="container py-16 flex items-center justify-center">
        <Card className="max-w-md w-full text-center shadow-lg">
          <CardHeader className="pt-12">
            <Loader2 className="h-16 w-16 mx-auto mb-4 text-primary animate-spin" />
            <CardTitle className="text-2xl">Loading Checkout Status</CardTitle>
            <CardDescription className="text-base">Please wait while we fetch your order details...</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  if (!status) {
    return (
      <div className="container py-16">
        <Card className="max-w-md mx-auto text-center shadow-lg">
          <CardHeader className="pt-12">
            <XCircle className="h-16 w-16 mx-auto mb-4 text-destructive" />
            <CardTitle className="text-2xl">Checkout Not Found</CardTitle>
            <CardDescription className="text-base mt-2">Unable to find checkout with ID: {checkoutId}</CardDescription>
          </CardHeader>
          <CardContent className="pb-12">
            <Button onClick={() => router.push("/")} size="lg">
              Return to Search
            </Button>
          </CardContent>
        </Card>
      </div>
    )
  }

  const overallStatus = status.nodes.some((n) => n.state === "FAILED")
    ? "FAILED"
    : status.nodes.every((n) => n.state === "COMPLETED")
      ? "COMPLETED"
      : "RUNNING"

  return (
    <div className="container py-8 space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => router.push("/cart")}>
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <div className="flex-1">
          <div className="flex items-center gap-2 text-sm text-muted-foreground mb-1">
            <span>Home</span>
            <span>/</span>
            <span>Cart</span>
            <span>/</span>
            <span className="text-foreground font-medium">Order {checkoutId.slice(0, 8)}</span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight">Checkout Status</h1>
        </div>
        <Badge
          variant={overallStatus === "COMPLETED" ? "default" : overallStatus === "FAILED" ? "destructive" : "secondary"}
          className="text-base px-4 py-2"
        >
          {overallStatus === "COMPLETED" ? "Completed" : overallStatus === "FAILED" ? "Failed" : "Processing"}
        </Badge>
      </div>

      {overallStatus === "COMPLETED" && (
        <Card className="border-success bg-success/5 shadow-md animate-in fade-in slide-in-from-top-4">
          <CardHeader>
            <div className="flex items-center gap-3">
              <div className="h-12 w-12 rounded-full bg-success/20 flex items-center justify-center">
                <CheckCircle2 className="h-7 w-7 text-success" />
              </div>
              <div>
                <CardTitle className="text-success text-xl">Order Confirmed!</CardTitle>
                <CardDescription className="text-base">Your order has been successfully processed</CardDescription>
              </div>
            </div>
          </CardHeader>
        </Card>
      )}

      {overallStatus === "FAILED" && (
        <Card className="border-destructive bg-destructive/5 shadow-md animate-in fade-in slide-in-from-top-4">
          <CardHeader>
            <div className="flex items-center gap-3">
              <div className="h-12 w-12 rounded-full bg-destructive/20 flex items-center justify-center">
                <XCircle className="h-7 w-7 text-destructive" />
              </div>
              <div className="flex-1">
                <CardTitle className="text-destructive text-xl">Order Failed</CardTitle>
                <CardDescription className="text-base">
                  Payment voided and inventory released. Please try again.
                </CardDescription>
              </div>
              <Button variant="destructive" onClick={() => router.push("/cart")}>
                Retry Checkout
              </Button>
            </div>
          </CardHeader>
        </Card>
      )}

      <div className="grid lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <Card className="shadow-md">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-xl">
                <Package className="h-6 w-6 text-primary" />
                Order Timeline
              </CardTitle>
              <CardDescription className="text-base">Real-time updates on your order processing</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-6">
                {status.nodes.map((node, index) => (
                  <div
                    key={node.nodeId}
                    className="flex gap-4 animate-in fade-in slide-in-from-left-4"
                    style={{ animationDelay: `${index * 100}ms` }}
                  >
                    <div className="relative flex flex-col items-center">
                      <div
                        className={`h-12 w-12 rounded-full flex items-center justify-center transition-all duration-300 ${
                          node.state === "COMPLETED"
                            ? "bg-success/20 ring-2 ring-success"
                            : node.state === "FAILED"
                              ? "bg-destructive/20 ring-2 ring-destructive"
                              : node.state === "RUNNING"
                                ? "bg-primary/20 ring-2 ring-primary animate-pulse"
                                : "bg-muted"
                        }`}
                      >
                        {getNodeIcon(node.state)}
                      </div>
                      {index < status.nodes.length - 1 && (
                        <div
                          className={`w-0.5 h-12 mt-2 transition-colors duration-300 ${
                            node.state === "COMPLETED" ? "bg-success" : "bg-border"
                          }`}
                        />
                      )}
                    </div>
                    <div className="flex-1 pb-6">
                      <div className="flex items-start justify-between gap-4 mb-2">
                        <div>
                          <h3 className="font-semibold text-lg">{node.nodeId}</h3>
                          <Badge variant={getNodeBadgeVariant(node.state)} className="mt-1">
                            {node.state}
                          </Badge>
                        </div>
                        {node.timestamp && (
                          <span className="text-sm text-muted-foreground">
                            {new Date(node.timestamp).toLocaleTimeString()}
                          </span>
                        )}
                      </div>
                      {node.message && <p className="text-sm text-muted-foreground leading-relaxed">{node.message}</p>}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <Card className="shadow-md lg:sticky lg:top-20">
            <CardHeader>
              <CardTitle className="text-lg">Order Summary</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Checkout ID</span>
                  <Button variant="ghost" size="sm" onClick={copyToClipboard} className="h-8 gap-2">
                    <code className="text-xs font-mono">{checkoutId.slice(0, 8)}...</code>
                    <Copy className="h-3 w-3" />
                  </Button>
                </div>
                <Separator />
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Status</span>
                  <Badge variant={getNodeBadgeVariant(overallStatus as NodeState)}>{overallStatus}</Badge>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Total Nodes</span>
                  <span className="font-medium">{status.nodes.length}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">Completed</span>
                  <span className="font-medium text-success">
                    {status.nodes.filter((n) => n.state === "COMPLETED").length}
                  </span>
                </div>
                {status.nodes.some((n) => n.state === "FAILED") && (
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">Failed</span>
                    <span className="font-medium text-destructive">
                      {status.nodes.filter((n) => n.state === "FAILED").length}
                    </span>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  )
}

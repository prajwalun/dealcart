"use client"

import { useEffect, useState, useRef } from "react"
import { useParams, useRouter } from "next/navigation"
import { CheckCircle2, XCircle, Clock, Loader2, AlertTriangle, Home } from "lucide-react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { API_BASE } from "@/lib/constants"
import type { NodeStatus, NodeState } from "@/lib/types"
import { cn } from "@/lib/utils"

const NODE_LABELS: Record<string, string> = {
  reserve: "Reserve Inventory",
  price: "Calculate Price",
  tax: "Calculate Tax",
  pay: "Process Payment",
  confirm: "Confirm Order",
  release: "Release Inventory",
  void: "Void Payment",
}

const NODE_ORDER = ["reserve", "price", "tax", "pay", "confirm"]

function getStateIcon(state: NodeState) {
  switch (state) {
    case "NODE_STATE_PENDING":
      return <Clock className="h-5 w-5 text-muted-foreground" />
    case "NODE_STATE_RUNNING":
      return <Loader2 className="h-5 w-5 text-primary animate-spin" />
    case "NODE_STATE_COMPLETED":
      return <CheckCircle2 className="h-5 w-5 text-success" />
    case "NODE_STATE_FAILED":
      return <XCircle className="h-5 w-5 text-destructive" />
    case "NODE_STATE_SKIPPED":
      return <AlertTriangle className="h-5 w-5 text-warning" />
    default:
      return <Clock className="h-5 w-5 text-muted-foreground" />
  }
}

function getStateBadgeVariant(state: NodeState): "default" | "secondary" | "destructive" | "outline" {
  switch (state) {
    case "NODE_STATE_COMPLETED":
      return "default"
    case "NODE_STATE_RUNNING":
      return "secondary"
    case "NODE_STATE_FAILED":
      return "destructive"
    default:
      return "outline"
  }
}

export default function CheckoutStatusPage() {
  const params = useParams()
  const router = useRouter()
  const checkoutId = params.checkoutId as string
  const [nodeStatuses, setNodeStatuses] = useState<Map<string, NodeStatus>>(new Map())
  const [isComplete, setIsComplete] = useState(false)
  const [hasRollback, setHasRollback] = useState(false)
  const [finalSuccess, setFinalSuccess] = useState(false)
  const eventSourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!checkoutId) return

    const url = `${API_BASE}/api/checkout/${checkoutId}/stream`
    const eventSource = new EventSource(url)
    eventSourceRef.current = eventSource

    eventSource.addEventListener("status", (e) => {
      try {
        const status: NodeStatus = JSON.parse(e.data)
        console.log("[v0] Received status update:", status)

        setNodeStatuses((prev) => {
          const newMap = new Map(prev)
          newMap.set(status.nodeId, status)
          return newMap
        })

        // Check for rollback nodes
        if (status.nodeId === "release" || status.nodeId === "void") {
          setHasRollback(true)
        }

        // Check if confirm completed successfully
        if (status.nodeId === "confirm" && status.state === "NODE_STATE_COMPLETED") {
          setFinalSuccess(true)
          setIsComplete(true)
        }

        // Check if any node failed
        if (status.state === "NODE_STATE_FAILED") {
          setIsComplete(true)
          setFinalSuccess(false)
        }
      } catch (error) {
        console.error("[v0] Failed to parse status:", error)
      }
    })

    eventSource.onerror = () => {
      console.log("[v0] EventSource closed")
      eventSource.close()
      setIsComplete(true)
    }

    return () => {
      eventSource.close()
    }
  }, [checkoutId])

  const getNodeDuration = (nodeId: string): string => {
    const status = nodeStatuses.get(nodeId)
    if (!status || status.state === "NODE_STATE_PENDING") return ""

    // Mock duration for demo
    return "0.5s"
  }

  return (
    <div className="container py-8 max-w-4xl">
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="text-2xl">Checkout Status</CardTitle>
              <CardDescription className="font-mono text-sm mt-1">Order ID: {checkoutId}</CardDescription>
            </div>
            {!isComplete && (
              <Badge variant="secondary" className="gap-1.5">
                <span className="relative flex h-2 w-2">
                  <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75"></span>
                  <span className="relative inline-flex rounded-full h-2 w-2 bg-primary"></span>
                </span>
                Processing
              </Badge>
            )}
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Rollback Warning */}
          {hasRollback && (
            <Alert variant="destructive">
              <AlertTriangle className="h-4 w-4" />
              <AlertTitle>Checkout Failed</AlertTitle>
              <AlertDescription>Payment voided and inventory released. Please try again.</AlertDescription>
            </Alert>
          )}

          {/* Timeline */}
          <div className="space-y-4">
            {NODE_ORDER.map((nodeId, index) => {
              const status = nodeStatuses.get(nodeId)
              const state = status?.state || "NODE_STATE_PENDING"
              const isActive = state === "NODE_STATE_RUNNING"
              const isLast = index === NODE_ORDER.length - 1

              return (
                <div key={nodeId} className="relative">
                  <div className="flex items-start gap-4">
                    {/* Icon */}
                    <div
                      className={cn(
                        "flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2",
                        state === "NODE_STATE_COMPLETED" && "border-success bg-success/10",
                        state === "NODE_STATE_RUNNING" && "border-primary bg-primary/10",
                        state === "NODE_STATE_FAILED" && "border-destructive bg-destructive/10",
                        state === "NODE_STATE_PENDING" && "border-border bg-muted",
                      )}
                    >
                      {getStateIcon(state)}
                    </div>

                    {/* Content */}
                    <div className="flex-1 space-y-1 pt-1">
                      <div className="flex items-center justify-between">
                        <h3 className="font-semibold text-foreground">{NODE_LABELS[nodeId] || nodeId}</h3>
                        <div className="flex items-center gap-2">
                          {getNodeDuration(nodeId) && (
                            <span className="text-xs text-muted-foreground font-mono">{getNodeDuration(nodeId)}</span>
                          )}
                          <Badge variant={getStateBadgeVariant(state)}>{state.replace("NODE_STATE_", "")}</Badge>
                        </div>
                      </div>
                      {status?.message && <p className="text-sm text-muted-foreground">{status.message}</p>}
                      {status?.errorMessage && <p className="text-sm text-destructive">{status.errorMessage}</p>}
                    </div>
                  </div>

                  {/* Connector Line */}
                  {!isLast && (
                    <div
                      className={cn(
                        "absolute left-5 top-10 h-8 w-0.5",
                        state === "NODE_STATE_COMPLETED" ? "bg-success" : "bg-border",
                      )}
                    />
                  )}
                </div>
              )
            })}

            {/* Rollback Nodes */}
            {hasRollback && (
              <>
                {["void", "release"].map((nodeId) => {
                  const status = nodeStatuses.get(nodeId)
                  if (!status) return null

                  return (
                    <div key={nodeId} className="relative">
                      <div className="flex items-start gap-4">
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border-2 border-warning bg-warning/10">
                          {getStateIcon(status.state)}
                        </div>
                        <div className="flex-1 space-y-1 pt-1">
                          <div className="flex items-center justify-between">
                            <h3 className="font-semibold text-foreground">{NODE_LABELS[nodeId] || nodeId}</h3>
                            <Badge variant={getStateBadgeVariant(status.state)}>
                              {status.state.replace("NODE_STATE_", "")}
                            </Badge>
                          </div>
                          {status.message && <p className="text-sm text-muted-foreground">{status.message}</p>}
                        </div>
                      </div>
                    </div>
                  )
                })}
              </>
            )}
          </div>

          {/* Final Status */}
          {isComplete && (
            <Alert variant={finalSuccess ? "default" : "destructive"}>
              {finalSuccess ? (
                <>
                  <CheckCircle2 className="h-4 w-4" />
                  <AlertTitle>Order Confirmed!</AlertTitle>
                  <AlertDescription>
                    Your order has been successfully processed. Order ID: {checkoutId}
                  </AlertDescription>
                </>
              ) : (
                <>
                  <XCircle className="h-4 w-4" />
                  <AlertTitle>Checkout Failed</AlertTitle>
                  <AlertDescription>
                    Unable to complete your order. Please try again or contact support.
                  </AlertDescription>
                </>
              )}
            </Alert>
          )}

          {/* Actions */}
          {isComplete && (
            <div className="flex justify-center gap-3 pt-4">
              <Button variant="outline" onClick={() => router.push("/")}>
                <Home className="h-4 w-4 mr-2" />
                Back to Home
              </Button>
              {!finalSuccess && <Button onClick={() => router.push("/cart")}>Try Again</Button>}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}

"use client"

import { create } from "zustand"
import { persist } from "zustand/middleware"
import type { CartItem } from "./types"

interface CartStore {
  items: CartItem[]
  addItem: (item: CartItem) => void
  removeItem: (productId: string, vendorId: string) => void
  updateQuantity: (productId: string, vendorId: string, quantity: number) => void
  clearCart: () => void
  getTotalAmount: () => number
}

export const useCartStore = create<CartStore>()(
  persist(
    (set, get) => ({
      items: [],
      addItem: (item) =>
        set((state) => {
          const existingIndex = state.items.findIndex(
            (i) => i.productId === item.productId && i.vendorId === item.vendorId,
          )
          if (existingIndex >= 0) {
            const newItems = [...state.items]
            newItems[existingIndex].quantity += item.quantity
            return { items: newItems }
          }
          return { items: [...state.items, item] }
        }),
      removeItem: (productId, vendorId) =>
        set((state) => ({
          items: state.items.filter((i) => !(i.productId === productId && i.vendorId === vendorId)),
        })),
      updateQuantity: (productId, vendorId, quantity) =>
        set((state) => ({
          items: state.items.map((i) =>
            i.productId === productId && i.vendorId === vendorId ? { ...i, quantity } : i,
          ),
        })),
      clearCart: () => set({ items: [] }),
      getTotalAmount: () => {
        const state = get()
        return state.items.reduce((total, item) => total + item.unitPrice * item.quantity, 0)
      },
    }),
    {
      name: "dealcart-storage",
    },
  ),
)

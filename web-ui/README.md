# DealCart+ Web UI

Next.js 15 frontend for DealCart+ with real-time SSE integration.

## Quick Start

### Local Development (Standalone)

```bash
# Install dependencies
pnpm install

# Create env file
echo "NEXT_PUBLIC_API_BASE_URL=http://localhost:8080" > .env.local

# Run dev server
pnpm dev
```

Visit http://localhost:3000

### With Docker Compose (Recommended)

From the repo root:
```bash
# Build all services
./mvnw clean package -DskipTests

# Start the entire stack
docker compose -f infra/docker-compose.yml up -d

# Visit the app
open http://localhost
```

## Features

- ✅ Real-time quote streaming via SSE
- ✅ Shopping cart with Zustand state
- ✅ Checkout with live DAG timeline
- ✅ Mobile responsive
- ✅ Toast notifications
- ✅ Error handling

## Environment Variables

Create `.env.local`:

```env
# Empty for same-origin (via Caddy)
NEXT_PUBLIC_API_BASE_URL=

# Or direct to gateway
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080

# Or production
NEXT_PUBLIC_API_BASE_URL=https://your-ec2-domain.com
```

## Tech Stack

- Next.js 15 (App Router)
- TypeScript
- Tailwind CSS
- shadcn/ui components
- Zustand (state management)
- Sonner (toasts)
- EventSource (SSE)

## API Integration

All API calls use `API_BASE` from `lib/constants.ts`:

- `GET /api/search?q={query}` - SSE quote stream
- `GET /api/quote?productId={id}&mode=best` - Single best quote
- `POST /api/checkout` - Start checkout
- `GET /api/checkout/{id}/stream` - SSE status stream

## Development

```bash
# Install
pnpm install

# Dev server with hot reload
pnpm dev

# Type check
pnpm build

# Production build
pnpm build && pnpm start
```

## Docker Build

```bash
docker build -t dealcart-ui .
docker run -p 3000:3000 dealcart-ui
```


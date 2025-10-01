# CI/CD Pipeline Documentation

## Overview

DealCart+ uses GitHub Actions for continuous integration and deployment, with Docker images stored in GitHub Container Registry (GHCR).

## Image Naming Convention

All Docker images follow this pattern:
```
ghcr.io/<owner>/<repo>-<service>:latest
ghcr.io/<owner>/<repo>-<service>:<sha>
```

### Examples:
- `ghcr.io/username/dealcart-vendor-pricing:latest`
- `ghcr.io/username/dealcart-vendor-pricing:abc1234`
- `ghcr.io/username/dealcart-edge-gateway:latest`
- `ghcr.io/username/dealcart-web-ui:latest`

## Local vs Remote Deployment

### Local Development
```bash
docker compose -f infra/docker-compose.yml up -d --build
```
- Uses `build:` sections in docker-compose.yml
- Builds images from local Dockerfiles
- Perfect for development and testing

### Remote Deployment
```bash
docker compose -f infra/docker-compose.yml pull
docker compose -f infra/docker-compose.yml up -d --remove-orphans
```
- Uses `image:` references in docker-compose.yml
- Pulls pre-built images from GHCR
- Perfect for production deployments

## Required Secrets for EC2 Deployment

The following secrets must be configured in GitHub repository settings:

| Secret | Description | Example |
|--------|-------------|---------|
| `EC2_HOST` | EC2 instance public IP or hostname | `ec2-1-2-3-4.compute-1.amazonaws.com` |
| `EC2_USER` | SSH username for EC2 instance | `ubuntu` or `ec2-user` |
| `EC2_KEY` | SSH private key for EC2 access | `-----BEGIN OPENSSH PRIVATE KEY-----...` |
| `GITHUB_TOKEN` | GitHub token for GHCR access | *(automatically provided by GitHub)* |

## Verification

### Local Build Test
```bash
# Test local build still works
docker compose -f infra/docker-compose.yml up -d --build
docker compose ps
```

**Output:**
```
NAME                     IMAGE                                             COMMAND                  SERVICE          CREATED          STATUS                             PORTS
caddy                    caddy:2-alpine                                    "caddy run --config …"   caddy            38 seconds ago   Up 2 seconds (health: starting)    0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp, 443/udp, 2019/tcp
checkout                 ghcr.io/testuser/dealcart-checkout:latest         "java -jar app.jar"      checkout         45 seconds ago   Up 37 seconds (healthy)            9200/tcp
infra-edge-gateway-1     ghcr.io/testuser/dealcart-edge-gateway:latest     "java -jar app.jar"      edge-gateway     39 seconds ago   Up 13 seconds (healthy)            8080/tcp
infra-edge-gateway-2     ghcr.io/testuser/dealcart-edge-gateway:latest     "java -jar app.jar"      edge-gateway     39 seconds ago   Up 13 seconds (healthy)            8080/tcp
infra-vendor-pricing-2   ghcr.io/testuser/dealcart-vendor-pricing:latest   "java -jar app.jar"      vendor-pricing   44 seconds ago   Up 26 seconds (healthy)            9100/tcp
infra-vendor-pricing-3   ghcr.io/testuser/dealcart-vendor-pricing:latest   "java -jar app.jar"      vendor-pricing   44 seconds ago   Up 25 seconds (healthy)            9100/tcp
infra-vendor-pricing-4   ghcr.io/testuser/dealcart-vendor-pricing:latest   "java -jar app.jar"      vendor-pricing   44 seconds ago   Up 24 seconds (healthy)            9100/tcp
vendor-mock-1            ghcr.io/testuser/dealcart-vendor-mock:latest      "java -jar app.jar"      vendor-mock-1    45 seconds ago   Up 36 seconds (healthy)            9101/tcp
vendor-mock-2            ghcr.io/testuser/dealcart-vendor-mock:latest      "java -jar app.jar"      vendor-mock-2    45 seconds ago   Up 36 seconds (healthy)            9101/tcp
web-ui                   ghcr.io/testuser/dealcart-web-ui:latest           "docker-entrypoint.s…"   web-ui           45 seconds ago   Up 37 seconds (health: starting)   3000/tcp
```

### GHCR Image Path
```bash
# Check GHCR image path
echo "Check GHCR: ghcr.io/${GITHUB_REPOSITORY}-edge-gateway:latest"
```

**Output:**
```
Check GHCR: ghcr.io/testuser/dealcart-edge-gateway:latest
```

## Workflow Status

After pushing to main branch, check the Actions tab in GitHub to verify:
- ✅ **Build job**: Maven compilation successful
- ✅ **Images job**: Docker images built and pushed to GHCR
- ⏳ **Deploy job**: Manual trigger ready for EC2 integration

## Next Steps

1. **Push to main** to trigger the first CI/CD run
2. **Verify images** appear in GHCR under your repository
3. **Configure EC2 secrets** when ready for deployment
4. **Test remote deployment** with `docker compose pull && docker compose up -d`

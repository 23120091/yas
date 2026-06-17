# Deployment Plan — YAS Platform

## Overview

GitOps-based continuous delivery using **Helm** + **ArgoCD** + **ArgoCD Image Updater** across 3 environments: `dev`, `staging`, `production`.

Two distinct update strategies:
- **dev**: CI builds & pushes → **ArgoCD Image Updater** watches registry, auto-detects new `dev-*` tags, updates Application parameters → ArgoCD deploys (CI never touches git for dev)
- **staging**: CI builds & pushes → **CI updates values files in repo** → ArgoCD detects git change → deploys
- **production**: same as staging but **manual Sync** in ArgoCD UI

```
┌──────────┐   push to main    ┌─────────────────────────────────┐
│ Developer │ ─────────────────→│  GitHub Actions CI                │
└──────────┘                   │  - Build Docker images            │
                               │  - Tag: dev-<short-sha>           │
                               │  - Push to ghcr.io/23120091       │
                               │  - NO git commit (Image Updater    │
                               │    handles deployment)             │
                               └─────────────────────────────────┘
                                        │
                                  ┌─────▼─────┐
                                  │  ghcr.io  │  Container Registry
                                  └─────┬─────┘
                                        │
                               ┌────────▼────────┐
                               │ ArgoCD Image     │  Polls registry every 2min
                               │ Updater          │  Detects new dev-* tag
                               │ (in-cluster)     │  → overrides Helm parameter
                               └────────┬────────┘  backend.image.tag in
                                        │            ArgoCD Application
                                        ▼
┌──────────┐  git tag v1.2.3 ┌─────────────────────────────────┐
│ Release  │ ───────────────→│  GitHub Actions CI (Tag)         │
│ Manager  │                 │  - Build images (:v1.2.3)       │
└──────────┘                 │  - Push to ghcr.io              │
                             │  - Update k8s/env/staging/*.yaml│
                             │  - Commit to main [skip-ci]     │
                             └─────────────────────────────────┘
                                        │
                                        ▼
                           ┌───────────────────────┐
                           │        ArgoCD          │  Watches k8s/ in repo
                           │  ┌──────┐ ┌───────┐   │
                           │  │ dev  │ │staging│   │  dev:     auto-sync (Image Updater driven)
                           │  │ app  │ │ app   │   │  staging:  auto-sync (CI commits to repo)
                           │  └──────┘ └───────┘   │  prod:     manual sync
                           │  ┌──────────┐         │
                           │  │production│         │
                           │  │ app      │         │
                           │  └──────────┘         │
                           └───────────────────────┘
                                        │
                                        ▼
┌───────────────────────────────────────────────────┐
│                 Kubernetes Cluster                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐ │
│  │ dev (ns) │  │ staging  │  │ production (ns)  │ │
│  │ node=dev │  │ node=stg │  │ node=prod        │ │
│  └──────────┘  └──────────┘  └──────────────────┘ │
└───────────────────────────────────────────────────┘
```

---

## 1. Directory Structure (additions)

All new files go under `k8s/`:

```
k8s/
├── argocd/
│   ├── image-updater/
│   │   └── image-updater.yaml            # ArgoCD Image Updater deployment + config
│   ├── projects/
│   │   └── yas-project.yaml              # AppProject with namespace restrictions
│   ├── applicationsets/
│   │   ├── dev-applicationset.yaml        # Dev services - auto sync + Image Updater annotations
│   │   ├── staging-applicationset.yaml    # Staging services - auto sync on tag update
│   │   └── production-applicationset.yaml # Prod services - manual sync only
│   └── bootstrap-app.yaml                # Root App that creates all ApplicationSets
├── env/
│   ├── dev/
│   │   ├── common-values.yaml            # NodeSelector, probes, global settings
│   │   ├── product-values.yaml           # Per-service resource + override
│   │   ├── cart-values.yaml
│   │   ├── order-values.yaml
│   │   ├── payment-values.yaml
│   │   ├── ... (one per service)
│   │   ├── storefront-ui-values.yaml
│   │   └── backoffice-ui-values.yaml
│   ├── staging/
│   │   ├── common-values.yaml
│   │   ├── product-values.yaml
│   │   └── ... (same structure)
│   └── production/
│       ├── common-values.yaml
│       ├── product-values.yaml
│       └── ... (same structure)
└── .github/
    └── workflows/
        ├── ci-dev.yaml                   # On push to main → build + push only
        ├── ci-staging.yaml               # On tag v* push → build + push + update values
        └── ci-production.yaml            # Manual workflow_dispatch → update values
```

---

## 2. Node Labeling

Label cluster nodes to separate environments:

```bash
kubectl label nodes <node1> env=dev
kubectl label nodes <node2> env=staging
kubectl label nodes <node3> env=production
```

Each env's `common-values.yaml` sets `nodeSelector`:

```yaml
# k8s/env/dev/common-values.yaml
backend:
  nodeSelector:
    env: dev
```

---

## 3. Resource Allocation

### Per-service, per-environment resource config

`k8s/env/dev/product-values.yaml`:
```yaml
backend:
  resources:
    requests:
      cpu: 100m
      memory: 256Mi
    limits:
      cpu: 500m
      memory: 512Mi
```

| Service | dev (req/lim) | staging (req/lim) | production (req/lim) |
|---|---|---|---|
| product | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| cart | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| order | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| payment | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| payment-paypal | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| customer | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| inventory | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| location | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| media | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| tax | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| promotion | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| rating | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| search | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| recommendation | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| sampledata | 50m/128Mi — 200m/256Mi | 50m/128Mi — 200m/256Mi | 50m/128Mi — 200m/256Mi |
| webhook | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| storefront-bff | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| backoffice-bff | 100m/256Mi — 500m/512Mi | 200m/512Mi — 1C/1Gi | 500m/1Gi — 2C/2Gi |
| storefront (ui) | 50m/128Mi — 200m/256Mi | 100m/256Mi — 300m/512Mi | 200m/512Mi — 500m/1Gi |
| backoffice (ui) | 50m/128Mi — 200m/256Mi | 100m/256Mi — 300m/512Mi | 200m/512Mi — 500m/1Gi |

---

## 4. Image Tagging & Update Strategy

| Environment | Tag | Update mechanism | Who updates? |
|---|---|---|---|
| dev | `dev-<short-sha>` | **ArgoCD Image Updater** polls registry, auto-updates Helm parameter | Image Updater |
| staging | semantic version `vX.Y.Z` | CI writes tag into `k8s/env/staging/*.yaml` then commits | GitHub Actions |
| production | semantic version `vX.Y.Z` | CI writes tag into `k8s/env/production/*.yaml` then commits | GitHub Actions (manual trigger) |

### How dev works (Image Updater)

```
  Push to main
       │
       ▼
  CI: build + push image (ghcr.io/.../product:dev-abc123)
       │  ← does NOT modify values.yaml, does NOT commit to git
       ▼
  ArgoCD Image Updater (polls every 2 min)
       │  Detects new dev-abc123 tag on registry
       ▼
  Overrides ArgoCD Application Helm parameter:
    backend.image.tag = "dev-abc123"
       │  (writes to ArgoCD internal state only, not git)
       ▼
  ArgoCD sees Application changed → auto-sync → deploys new pod
```

### How staging works (CI commit)

```
  Git tag v1.2.3 pushed
       │
       ▼
  CI: build + push image (ghcr.io/.../product:v1.2.3)
       │
       ▼
  CI: yq -i ".backend.image.tag = \"v1.2.3\"" k8s/env/staging/product-values.yaml
       │  git commit + push [skip-ci]
       ▼
  ArgoCD detects git change → auto-sync → deploys new pod
```

### How production works (manual)

```
  workflow_dispatch (select tag v1.2.3)
       │
       ▼
  CI: yq -i ".backend.image.tag = \"v1.2.3\"" k8s/env/production/product-values.yaml
       │  git commit + push [skip-ci]
       ▼
  ArgoCD detects git change → Application becomes "OutOfSync"
       │  (because syncPolicy: {} — NO auto-sync)
       ▼
  Approver opens ArgoCD UI → clicks "Sync" → deploys to production
```

### Why different strategies?

| Criterion | dev (Image Updater) | staging/prod (CI commit) |
|---|---|---|
| Speed | Fast (no CI git commit needed) | Slower (needs CI commit + push) |
| Audit trail | Tag traceable in registry | Tag stored in git (full deploy history) |
| Reproducibility | Lower (tag overwritten in ArgoCD state) | High (git is source of truth) |
| Best for | Continuous dev iteration, no rollback needed | Audit trail required, must know exact version running |

---

## 5. ArgoCD Configuration

### 5.1 Bootstrap (manual, one-time)

```bash
# Install ArgoCD
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

# Apply the bootstrap application
kubectl apply -f k8s/argocd/bootstrap-app.yaml
```

### 5.2 AppProject

`k8s/argocd/projects/yas-project.yaml`:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: yas
  namespace: argocd
spec:
  sourceRepos:
    - https://github.com/23120091/yas
  destinations:
    - namespace: dev
      server: https://kubernetes.default.svc
    - namespace: staging
      server: https://kubernetes.default.svc
    - namespace: production
      server: https://kubernetes.default.svc
```

### 5.3 ApplicationSet Design — Dev (with Image Updater annotations)

`k8s/argocd/applicationsets/dev-applicationset.yaml`:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: yas-dev
  namespace: argocd
spec:
  generators:
    - list:
        elements:
          - service: product
          - service: cart
          - service: order
          - service: payment
          - service: payment-paypal
          - service: customer
          - service: inventory
          - service: location
          - service: media
          - service: tax
          - service: promotion
          - service: rating
          - service: search
          - service: recommendation
          - service: sampledata
          - service: webhook
          - service: storefront-bff
          - service: backoffice-bff
  template:
    metadata:
      name: "{{service}}-dev"
      labels:
        env: dev
      # ============================================================
      # ARGOCD IMAGE UPDATER ANNOTATIONS
      # ============================================================
      # Each Application declares which images to auto-update.
      # Image Updater reads these annotations to know:
      #   - Which registry to poll
      #   - Which Helm parameter to override
      #   - Which tag selection strategy (newest-build, semver, latest...)
      #   - Where to write the result (argocd internal state or git)
      # ============================================================
      annotations:
        # Image alias list: <alias>=<full-registry-path>
        # Each service gets one alias (usually matches the service name)
        argocd-image-updater.argoproj.io/image-list: "{{service}}=ghcr.io/23120091/{{service}}"

        # Tag selection strategy:
        #   newest-build → always pick the most recently pushed tag (by build timestamp)
        #   Best for dev: no semver parsing, no alphabetical sorting needed.
        #   The latest dev-abc123 tag gets picked immediately.
        argocd-image-updater.argoproj.io/{{service}}.update-strategy: newest-build

        # Filter: only accept tags starting with "dev-"
        # Prevents accidentally picking "latest", "v1.2.3", or tags from other environments
        argocd-image-updater.argoproj.io/{{service}}.allow-tags: regexp:^dev-.*

        # Helm parameters to override when updating the image
        #   image-name:  path to the image repository field
        #   image-tag:   path to the image tag field
        # Image Updater will set: backend.image.tag = "dev-<new-sha>"
        argocd-image-updater.argoproj.io/{{service}}.helm.image-name: backend.image.repository
        argocd-image-updater.argoproj.io/{{service}}.helm.image-tag: backend.image.tag

        # Write-back method:
        #   argocd → only write to ArgoCD internal parameter state
        #   Does NOT commit to git → fast, no CI loop risk
        #   If Application is refreshed, value reverts to git (tag: latest),
        #   but Image Updater will detect and update again on the next poll cycle
        argocd-image-updater.argoproj.io/write-back-method: argocd

        # Force update: always update even if the current tag is the same
        # Useful for force-redeploying the same tag (e.g., restart pod)
        # argocd-image-updater.argoproj.io/force-update: "true"

    spec:
      project: yas
      source:
        repoURL: https://github.com/23120091/yas
        targetRevision: main
        path: "k8s/charts/{{service}}"
        helm:
          valueFiles:
            - "../../env/dev/common-values.yaml"
            - "../../env/dev/{{service}}-values.yaml"
      destination:
        server: https://kubernetes.default.svc
        namespace: dev
      syncPolicy:
        automated:
          prune: true
          selfHeal: true
          allowEmpty: true
        syncOptions:
          - CreateNamespace=true
```

### 5.4 ApplicationSet Design — Staging (CI-driven, no Image Updater)

`k8s/argocd/applicationsets/staging-applicationset.yaml` — same structure, NO Image Updater annotations:
```yaml
# Key differences from dev:
#   - NO argocd-image-updater annotations
#   - valueFiles: ../../env/staging/...
#   - namespace: staging
#   - targetRevision: main (CI already committed tag into values files on main)
#   - Image tag managed by CI (writes to values file then commits)
```

### 5.5 Production ApplicationSet (manual sync only)

```yaml
# Key differences:
#   - valueFiles: ../../env/production/...
#   - namespace: production
#   - syncPolicy: {}   ← manual sync only
#   - NO Image Updater annotations
```

### 5.6 ArgoCD Image Updater — Installation & Config

#### Installation

```bash
# Install ArgoCD Image Updater into argocd namespace
kubectl apply -n argocd -f \
  https://raw.githubusercontent.com/argoproj-labs/argocd-image-updater/stable/manifests/install.yaml

# Or use Helm (recommended for version management)
# helm repo add argo https://argoproj.github.io/argo-helm
# helm upgrade --install argocd-image-updater argo/argocd-image-updater \
#   --namespace argocd --create-namespace
```

#### Configuring registry credentials

Image Updater needs pull access to fetch image metadata from GHCR. Create a secret with a GitHub token:

```bash
# Secret containing GitHub token (read-only packages scope is sufficient)
kubectl create secret docker-registry ghcr-credentials \
  --namespace argocd \
  --docker-server=ghcr.io \
  --docker-username=<github-username> \
  --docker-password=<github-token> \
  --docker-email=<email>
```

Then patch the `argocd-image-updater` ConfigMap to register the registry:

```yaml
# kubectl edit configmap argocd-image-updater-config -n argocd
apiVersion: v1
kind: ConfigMap
metadata:
  name: argocd-image-updater-config
  namespace: argocd
data:
  registries.conf: |
    registries:
      - name: ghcr.io
        api_url: https://ghcr.io
        ping: no
        prefix: ghcr.io
        credentials: pullsecret:argocd/ghcr-credentials
        defaultns: 23120091
        # defaultns: GHCR namespace (user/org name)
```

#### How it works

```
ArgoCD Image Updater (background service running in cluster)
  │
  │  Poll interval: 2 minutes (default)
  │
  ├──► Iterates all Applications with annotation
  │      argocd-image-updater.argoproj.io/image-list
  │
  ├──► For each image alias:
  │      │  Calls GHCR API: GET /v2/<name>/tags/list
  │      │  Filters tags by allow-tags pattern (^dev-.*)
  │      │  Sorts by update-strategy (newest-build: sort by push time)
  │      │  Compares with current tag in Application
  │      │  If different → update
  │      │
  │      ▼
  │  Overrides Helm parameter:
  │    backend.image.tag = "dev-abc123"
  │    (writes to ArgoCD Application internal state, not git)
  │
  ├──► ArgoCD detects Application parameter changed
  │      → Triggers auto-sync (if syncPolicy.automated)
  │      → Deploys pod with new image
```

#### Common strategies

| Strategy | Behavior | Best for |
|---|---|---|
| `newest-build` | Pick the most recent tag by build timestamp | dev (constant `dev-*` pushes) |
| `semver` | Sort by semver, pick the latest | staging/production (if not using CI commit) |
| `latest` | Always pick the `latest` tag | Not recommended (not traceable) |
| `name` | Sort alphabetically, pick the last | Rarely used |
| `digest` | Track the digest SHA of a specific tag | Production (pin exact SHA) |

#### Debugging

```bash
# View Image Updater logs
kubectl logs -n argocd -l app.kubernetes.io/name=argocd-image-updater -f

# View Image Updater status on an Application
kubectl describe application product-dev -n argocd | grep -A20 "Image Updater"

# Force immediate update (skip poll interval)
# Add then remove this annotation:
#   argocd-image-updater.argoproj.io/force-update: "true"
```

---

## 6. CI/CD Pipelines (GitHub Actions)

### 6.1 Dev Pipeline — `.github/workflows/ci-dev.yaml`

**Trigger:** `push` to `main` branch

**Only builds + pushes images. Does NOT modify git.** ArgoCD Image Updater auto-detects new tags and deploys.

```yaml
name: CI - Dev
on:
  push:
    branches: [main]

jobs:
  detect-changes:
    runs-on: ubuntu-latest
    outputs:
      services: ${{ steps.changed.outputs.services }}
    steps:
      - uses: actions/checkout@v4
      - name: Detect changed services
        id: changed
        run: |
          # Scan changed paths, map to Docker image names
          echo "services=$(...)" >> $GITHUB_OUTPUT

  build-and-push:
    needs: detect-changes
    # Only run if commit is NOT [skip-ci]
    if: "!contains(github.event.head_commit.message, '[skip-ci]')"
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: ${{ fromJSON(needs.detect-changes.outputs.services) }}
    steps:
      - uses: actions/checkout@v4
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build & Push
        uses: docker/build-push-action@v6
        with:
          context: .
          file: docker/Dockerfile
          push: true
          tags: ghcr.io/23120091/${{ matrix.service }}:dev-${{ github.sha }}
          build-args: |
            SERVICE=${{ matrix.service }}

  # NO "update-env-files" step and NO "commit back to repo"
  # → Image Updater will automatically detect new dev-<sha> tags and deploy
  # → CI never touches git, eliminating infinite loop risk
```

### 6.2 Staging Pipeline — `.github/workflows/ci-staging.yaml`

**Trigger:** push a tag matching `v*` (e.g. `v1.2.3`)

```yaml
name: CI - Staging
on:
  push:
    tags:
      - 'v*'

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service:
          - product
          - cart
          - order
          - payment
          - payment-paypal
          - customer
          - inventory
          - location
          - media
          - tax
          - promotion
          - rating
          - search
          - recommendation
          - sampledata
          - webhook
          - storefront-bff
          - backoffice-bff
          - storefront-ui
          - backoffice-ui
    steps:
      - uses: actions/checkout@v4
      - name: Login to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build & Push
        uses: docker/build-push-action@v6
        with:
          context: .
          file: docker/Dockerfile
          push: true
          tags: ghcr.io/23120091/${{ matrix.service }}:${{ github.ref_name }}
          build-args: |
            SERVICE=${{ matrix.service }}

  update-staging-values:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Update image tags in staging values
        run: |
          for svc in $SERVICES; do
            yq -i ".backend.image.tag = \"${{ github.ref_name }}\"" \
              "k8s/env/staging/${svc}-values.yaml"
          done
      - name: Commit & Push
        run: |
          git config user.name "ci-bot"
          git config user.email "ci-bot@yas.dev"
          git add k8s/env/staging/
          git commit -m "ci(staging): deploy ${{ github.ref_name }} to staging [skip-ci]" || true
          git push
```

**Optional workflow refinement:** Also create a branch `release/${{ github.ref_name }}` from the tag for hotfix reference:

```bash
git checkout -b "release/${{ github.ref_name }}" "${{ github.ref_name }}"
git push origin "release/${{ github.ref_name }}"
```

### 6.3 Production Pipeline — `.github/workflows/ci-production.yaml`

**Trigger:** `workflow_dispatch` (manual trigger with tag input)

```yaml
name: CI - Production
on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Version tag to deploy (e.g. v1.2.3)'
        required: true
        type: string
      service:
        description: 'Service to deploy (leave empty for all)'
        required: false
        type: string
        default: 'all'

jobs:
  deploy-to-production:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Update image tags in production values
        run: |
          if [ "${{ inputs.service }}" = "all" ]; then
            for f in k8s/env/production/*-values.yaml; do
              yq -i ".backend.image.tag = \"${{ inputs.version }}\"" "$f"
            done
          else
            yq -i ".backend.image.tag = \"${{ inputs.version }}\"" \
              "k8s/env/production/${{ inputs.service }}-values.yaml"
          fi
      - name: Commit & Push
        run: |
          git config user.name "ci-bot"
          git config user.email "ci-bot@yas.dev"
          git add k8s/env/production/
          git commit -m "ci(prod): stage ${{ inputs.version }} for production [skip-ci]" || true
          git push

  # *** After this job, a human must manually click "Sync" in ArgoCD ***
  # to deploy to production. This is enforced by syncPolicy: {} in the
  # production ApplicationSet.
```

### 6.4 Infinite Loop Prevention

Every CI commit includes `[skip-ci]` in the message. The CI workflows check:

```yaml
if: "!contains(github.event.head_commit.message, '[skip-ci]')"
```

This ensures CI → commit → ArgoCD sync does not re-trigger CI.

---

## 7. Deployment Flow Summary

```
 Dev (Image Updater)           Staging (CI commit)          Production (CI + manual)
 ─────────────────                 ──────────                  ──────────

 push to main                   git tag v1.2.3              Manual trigger
      │                              │                      (workflow_dispatch)
      ▼                              ▼                              │
 Build image:dev-abc           Build image:v1.2.3                  │
 Push to ghcr.io               Push to ghcr.io                     │
      │                              │                              │
      ▼                              ▼                              ▼
 (NO git commit)               CI edits values.yaml          CI edits values.yaml
                               writes tag v1.2.3            writes tag v1.2.3
                                     │                              │
                                     ▼                              ▼
                               Commit [skip-ci]              Commit [skip-ci]
                                     │                              │
      │                              ▼                              ▼
      │                        ArgoCD auto-sync              ArgoCD OutOfSync
      │                        → staging ns                  (no auto-sync)
      ▼                                                             │
 Image Updater polls registry                                Approver opens UI
 Detects newest dev-abc                                              │
 Updates Helm param:                                                ▼
   backend.image.tag=dev-abc                                 Clicks "Sync"
      │                                                      → production ns
      ▼
 ArgoCD auto-sync
 → dev ns
```

### Timeline comparison

| Step | Dev (Image Updater) | Staging (CI commit) | Production |
|---|---|---|---|
| 1. Build + push | CI (~3 min) | CI (~5 min, builds all) | No build (reuses image) |
| 2. Update tag ref | Image Updater (automatic) | CI commits to git | CI commits to git |
| 3. Detect change | Image Updater poll (≤ 2 min) | ArgoCD polls git (≤ 3 min) | ArgoCD polls git |
| 4. Deploy | Automatic | Automatic | Wait for human Sync click |
| Total time | ~5 min | ~8 min | Depends on approver |

---

## 8. Quick Start Checklist

- [ ] Label nodes with `env=dev`, `env=staging`, `env=production`
- [ ] Install ArgoCD on cluster
- [ ] Install ArgoCD Image Updater (section 5.6)
- [ ] Create `ghcr-credentials` secret in argocd namespace
- [ ] Configure Image Updater ConfigMap with GHCR registry
- [ ] Apply `k8s/argocd/projects/yas-project.yaml`
- [ ] Apply `k8s/argocd/bootstrap-app.yaml`
- [ ] Create `k8s/env/{dev,staging,production}/` directories with values files
- [ ] Create `k8s/argocd/applicationsets/*.yaml`
- [ ] Add `.github/workflows/ci-dev.yaml` (build + push only, no git commit)
- [ ] Add `.github/workflows/ci-staging.yaml`
- [ ] Add `.github/workflows/ci-production.yaml`
- [ ] Set `GHCR_TOKEN` secret in GitHub repo
- [ ] Push to main → verify dev Image Updater detects and deploys
- [ ] Create tag `v0.1.0` → verify staging auto-deploys
- [ ] Trigger `ci-production` workflow → verify manual Sync required in ArgoCD

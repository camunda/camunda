# syntax=docker/dockerfile:1.7

FROM golang:1.25-alpine AS builder

WORKDIR /src

COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod \
    go mod download

COPY . .

ARG TARGETOS=linux
ARG TARGETARCH=amd64
RUN --mount=type=cache,target=/go/pkg/mod \
    --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 GOOS=${TARGETOS} GOARCH=${TARGETARCH} \
    go build -trimpath -ldflags="-s -w" \
    -o /out/metrics-exporter .

FROM gcr.io/distroless/static-debian13:nonroot

COPY --from=builder /out/metrics-exporter /usr/local/bin/metrics-exporter

EXPOSE 9600
USER nonroot:nonroot

ENTRYPOINT ["/usr/local/bin/metrics-exporter"]
CMD ["--web.listen-address=:9600"]

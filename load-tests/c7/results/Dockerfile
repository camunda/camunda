# Results collector: loadtestctl (from camunda/camunda, pinned) + AWS CLI + collect.sh.
# Build context: results/. docker build -t c7-bench/results results
FROM python:3.13-slim

ARG CAMUNDA_REF=237f417f5cd8f002c2a93d6aeab2546300c0d7c7

RUN apt-get update \
 && apt-get install -y --no-install-recommends git ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && pip install --no-cache-dir uv awscli

# Only load-tests/loadtestctl is needed from the camunda monorepo.
RUN git init /opt/camunda \
 && cd /opt/camunda \
 && git remote add origin https://github.com/camunda/camunda.git \
 && git sparse-checkout set load-tests/loadtestctl \
 && git fetch --depth 1 --filter=blob:none origin "$CAMUNDA_REF" \
 && git checkout FETCH_HEAD \
 && uv tool install /opt/camunda/load-tests/loadtestctl \
 && rm -rf /opt/camunda/.git

ENV PATH="/root/.local/bin:${PATH}"
WORKDIR /results
COPY collect.sh report-queries-c7.yaml /opt/c7-results/
ENV OUT_DIR=/results
ENTRYPOINT ["/opt/c7-results/collect.sh"]

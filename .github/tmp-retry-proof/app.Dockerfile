# THROWAWAY — minimal image for the retry proof. The `app` stage name is required because
# docker-build-with-retry hard-codes `target: app`.
FROM alpine:3.22 AS app
RUN echo "retry proof" > /proof.txt

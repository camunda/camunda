 ---
1. HDFS — verify init-hdfs created the directories

docker exec greatbearlake-namenode-1 hdfs dfs -ls /
Expected: /flink, /user, /zeebe listed.

---
2. Trino — query the Iceberg catalog

Open a Trino CLI session:
docker exec -it greatbearlake-trino-1 trino
Then run:
SHOW CATALOGS;
SHOW SCHEMAS IN iceberg;
-- Once data is ingested:
SELECT COUNT(*) FROM iceberg.zeebe.zeebe_records;

---
3. Full end-to-end — Zeebe → HDFS → Flink → Trino

Step 1 — Fix the Flink 2.x API break in IcebergIngestionJob.java

EnvironmentSettings.newInstance() was removed in Flink 2.0. One line needs updating:
// Before (Flink 1.x):
final var settings = EnvironmentSettings.newInstance().inStreamingMode().build();

// After (Flink 2.x):
final var settings = EnvironmentSettings.inStreamingMode();

Step 2 — Build the fat JAR
bash greatbearlake/setup.sh

Step 3 — Start Zeebe with the parquet exporter

In greatbearlake/application-lakehouse.yaml, the exporter is already configured to write to hdfs://localhost:9000/zeebe/export. Start Zeebe with:
./mvnw -pl dist spring-boot:run \
-Dspring-boot.run.arguments=--spring.config.additional-location=greatbearlake/application-lakehouse.yaml
Let it run for ~30 seconds to accumulate some records, then verify files landed:
docker exec greatbearlake-namenode-1 hdfs dfs -ls -R /zeebe/export

Step 4 — Submit the Flink ingestion job
docker compose --profile ingestion up -d iceberg-ingestion
docker logs -f greatbearlake-iceberg-ingestion-1
Expected: ==> Job submitted successfully

Step 5 — Query in Trino
docker exec -it greatbearlake-trino-1 trino
SELECT record_type, value_type, COUNT(*) AS n
FROM iceberg.zeebe.zeebe_records
GROUP BY 1, 2
ORDER BY n DESC
LIMIT 20;

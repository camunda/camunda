/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.camunda.analytics.lake.write.LocalFileIO;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.FileScanTask;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.io.CloseableIterable;
import org.apache.iceberg.jdbc.JdbcCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minimal, self-contained demo UI for the lake PoC, built on nothing but the JDK's own {@code
 * com.sun.net.httpserver} — no servlet container, no new dependency.
 *
 * <p>It owns its <b>own</b> embedded, read-only DuckDB connection — entirely separate from {@link
 * io.camunda.analytics.lake.write.IcebergLakeWriter}'s writer connection — so browsing the lake
 * from a browser tab can never contend with, or block, the translator's own flush path. Both
 * connections are simply independent {@code jdbc:duckdb:} in-process instances; DuckDB doesn't
 * require (or support) sharing one connection across unrelated concerns like this.
 *
 * <h2>How it reads the data</h2>
 *
 * <p>Rather than the Iceberg {@code iceberg_scan} table function (which would need the {@code
 * iceberg} DuckDB extension installed — a network fetch this offline-friendly PoC deliberately
 * avoids, see {@link io.camunda.analytics.lake.write.IcebergLakeWriter}'s own javadoc), this server
 * points DuckDB's {@code read_parquet} at Parquet files. How it gets that file list differs by
 * view:
 *
 * <ul>
 *   <li>{@code instances} / {@code activities} — this server opens its <b>own, independent</b>
 *       {@link JdbcCatalog} handle (see {@link #openCatalog(Path)}) against the exact same H2
 *       catalog/warehouse {@link io.camunda.analytics.lake.write.IcebergLakeWriter} uses, then
 *       reads the CURRENT snapshot's live data-file list off that catalog's {@code Table} handles
 *       (see {@link #ensureIcebergBackedView} for why this — not a directory glob — is required for
 *       correctness). It deliberately does <em>not</em> share the writer's own {@code
 *       Table}/catalog instances: those are committed to from the poll-loop thread, and this class
 *       is called from HTTP request-handler threads, so sharing one {@code Table} instance across
 *       the two would be an unsynchronized concurrent-access hazard. A second, independent {@link
 *       JdbcCatalog} pointed at the same H2 file is safe — H2 in embedded mode already serves
 *       multiple connections to one database file from within a single JVM (this is exactly how
 *       {@code JdbcCatalog}'s own internal connection pool already works today).
 *   <li>{@code open_instances} / {@code open_elements} — still a single-file {@code read_parquet}
 *       glob straight off {@link io.camunda.analytics.lake.state.StateSnapshotDumper}'s dump
 *       directory ({@code <stateDir>/_snapshot/open_instances.parquet} and {@code
 *       open_elements.parquet}). Globbing these is correct: {@code StateSnapshotDumper} writes a
 *       new file and atomically replaces the old one, there is no Iceberg table (and therefore no
 *       compaction-retained old copy) involved at all.
 * </ul>
 *
 * <h2>Lazy, per-request views</h2>
 *
 * <p>Before the translator has flushed anything, none of the backing tables/files exist yet — a
 * {@code read_parquet} view on a still-missing file (or an Iceberg table this server hasn't been
 * able to load yet) throws at <em>view creation</em> time (DuckDB has to resolve the schema to bind
 * a view), not merely at query time. So all four views ({@code instances}, {@code activities},
 * {@code open_instances}, {@code open_elements}) are (re)created fresh on every {@code /api/query}
 * request, skipping any that aren't available yet. A query that references a still-missing view
 * gets a friendly {@code {"error": ...}} response instead of the server blowing up — this is a
 * local demo tool, not production code, so favoring simplicity (recreate every request) over
 * caching is the right trade-off at this scale.
 */
public final class LakeUiServer implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(LakeUiServer.class);

  /** Row cap on {@code /api/query} responses -- this is a demo tool, not a paginated API. */
  private static final int MAX_ROWS = 500;

  /** Best-effort per-statement timeout so a runaway free-form query can't hang the UI forever. */
  private static final int QUERY_TIMEOUT_SECONDS = 15;

  private static final String INSTANCES_VIEW = "instances";
  private static final String ACTIVITIES_VIEW = "activities";
  private static final String OPEN_INSTANCES_VIEW = "open_instances";
  private static final String OPEN_ELEMENTS_VIEW = "open_elements";

  /** Every canned view {@link #ensureAllCannedViews()} knows how to (re)create. */
  private static final List<String> CANNED_VIEWS =
      List.of(INSTANCES_VIEW, ACTIVITIES_VIEW, OPEN_INSTANCES_VIEW, OPEN_ELEMENTS_VIEW);

  private final HttpServer httpServer;
  private final ExecutorService requestExecutor;
  private final Connection duckdb;

  // File(s) backing each open-state view -- computed once from the constructor's paths, mirroring
  // StateSnapshotDumper's dump directory exactly.
  private final Path openInstancesFile;
  private final Path openElementsFile;

  // Explicit lake.bpmnDir override for the /process-map page's BPMN discovery (see BpmnCatalog's
  // javadoc for the resolution order used when this is null). Re-resolved on every /process-map
  // request rather than cached -- see #handleProcessMapCatalog -- so newly-added .bpmn files show
  // up without restarting this server, matching this file's overall "lazy, per-request" philosophy.
  private final Path bpmnDirOverride;

  // This server's OWN, independent catalog handle -- never the writer's, see class javadoc. All
  // are lazily populated (null until available) by #ensureCatalogAndTablesLoaded, since the
  // translator may not have created the warehouse/tables yet when a request first comes in.
  private final Path warehouseDir;
  private JdbcCatalog catalog;
  private Table instancesTable;
  private Table activitiesTable;

  public LakeUiServer(final int port, final Path warehouseDir, final Path stateDir) {
    this(port, warehouseDir, stateDir, null);
  }

  /**
   * @param bpmnDirOverride explicit {@code lake.bpmnDir} override for the {@code /process-map}
   *     page's BPMN discovery, or {@code null} to use {@link BpmnCatalog}'s default resolution
   */
  public LakeUiServer(
      final int port, final Path warehouseDir, final Path stateDir, final Path bpmnDirOverride) {
    this.warehouseDir = warehouseDir;
    this.bpmnDirOverride = bpmnDirOverride;
    // Mirrors StateSnapshotDumper: dumpDir is stateDir/_snapshot, files are named
    // open_instances.parquet / open_elements.parquet inside it.
    final Path snapshotDir = stateDir.resolve("_snapshot");
    openInstancesFile = snapshotDir.resolve(OPEN_INSTANCES_VIEW + ".parquet");
    openElementsFile = snapshotDir.resolve(OPEN_ELEMENTS_VIEW + ".parquet");

    try {
      duckdb = DriverManager.getConnection("jdbc:duckdb:");
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to open embedded DuckDB connection for the UI", e);
    }

    try {
      // Loopback-only: this is a local demo tool with no auth, never meant to be reachable off
      // the box it runs on.
      httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to bind lake UI server to port " + port, e);
    }
    // A tiny fixed pool rather than the JDK default (which runs the accepting thread's own
    // sequential executor) -- keeps one slow free-form query from blocking the canned tiles.
    requestExecutor = Executors.newFixedThreadPool(4);
    httpServer.setExecutor(requestExecutor);
    httpServer.createContext("/", this::handleIndex);
    httpServer.createContext("/api/query", this::handleQuery);
    httpServer.createContext("/dashboard", this::handleDashboard);
    httpServer.createContext("/process-map", this::handleProcessMapPage);
    httpServer.createContext("/api/process-map/catalog", this::handleProcessMapCatalog);
    httpServer.createContext("/api/process-map/model", this::handleProcessMapModel);
    httpServer.createContext("/api/process-map", this::handleProcessMapData);
  }

  public void start() {
    httpServer.start();
  }

  /**
   * The actual bound port -- useful when constructed with port {@code 0} (OS-assigned ephemeral
   * port), which a test does to avoid colliding with another test or a locally-running instance of
   * this same server. Package-private: production callers always pass an explicit port.
   */
  int boundPort() {
    return httpServer.getAddress().getPort();
  }

  @Override
  public void close() {
    httpServer.stop(0);
    requestExecutor.shutdownNow();
    try {
      duckdb.close();
    } catch (final SQLException e) {
      LOG.warn("Failed to close the UI's embedded DuckDB connection", e);
    }
    if (catalog != null) {
      catalog.close();
    }
  }

  // ---------------------------------------------------------------------------------------------
  // HTTP handlers
  // ---------------------------------------------------------------------------------------------

  private void handleIndex(final HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendPlainText(exchange, 405, "Method Not Allowed");
      return;
    }
    sendBytes(
        exchange, 200, "text/html; charset=utf-8", INDEX_HTML.getBytes(StandardCharsets.UTF_8));
  }

  private void handleDashboard(final HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendPlainText(exchange, 405, "Method Not Allowed");
      return;
    }
    sendBytes(
        exchange, 200, "text/html; charset=utf-8", DASHBOARD_HTML.getBytes(StandardCharsets.UTF_8));
  }

  private void handleQuery(final HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      sendPlainText(exchange, 405, "Method Not Allowed");
      return;
    }
    final String sql =
        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
    final String json = sql.isEmpty() ? jsonError("Empty query") : runGuarded(sql);
    sendBytes(
        exchange, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
  }

  // ---------------------------------------------------------------------------------------------
  // Process map -- BPMN heatmap page + its supporting endpoints. See BpmnCatalog/ProcessMapService
  // for the discovery/query logic this only wires up to HTTP.
  // ---------------------------------------------------------------------------------------------

  private void handleProcessMapPage(final HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendPlainText(exchange, 405, "Method Not Allowed");
      return;
    }
    sendBytes(
        exchange,
        200,
        "text/html; charset=utf-8",
        PROCESS_MAP_HTML.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * {@code GET /api/process-map/catalog} -- the process picker's data source: every BPMN process id
   * discovered on disk (see {@link BpmnCatalog}) that also has data in {@code activities}, plus
   * (for the page's "no match" hint) every data process id and every directory that was scanned.
   */
  private void handleProcessMapCatalog(final HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendPlainText(exchange, 405, "Method Not Allowed");
      return;
    }
    ensureAllCannedViews();
    final BpmnCatalog.ScanResult scan = BpmnCatalog.discover(bpmnDirOverride);
    List<String> dataProcessIds;
    try {
      dataProcessIds =
          viewExists(ACTIVITIES_VIEW)
              ? ProcessMapService.dataProcessIds(duckdb, ACTIVITIES_VIEW)
              : List.of();
    } catch (final SQLException e) {
      dataProcessIds = List.of();
      LOG.debug("Failed to read data process ids: {}", e.getMessage());
    }
    final Set<String> dataProcessIdSet = Set.copyOf(dataProcessIds);
    final List<BpmnCatalog.BpmnModel> matched =
        BpmnCatalog.matching(scan.models(), dataProcessIdSet);

    final String json =
        JsonSupport.object(
            List.of(
                JsonSupport.member(
                    "dataProcessIds", JsonSupport.arrayOf(dataProcessIds, JsonSupport::quote)),
                JsonSupport.member(
                    "scannedDirs",
                    JsonSupport.arrayOf(
                        scan.scannedDirs(), dir -> JsonSupport.quote(dir.toString()))),
                JsonSupport.member(
                    "matchedProcesses",
                    JsonSupport.arrayOf(
                        matched,
                        model ->
                            JsonSupport.object(
                                List.of(
                                    JsonSupport.stringMember("processId", model.processId()),
                                    JsonSupport.stringMember(
                                        "processName",
                                        model.processName().isBlank()
                                            ? model.processId()
                                            : model.processName()),
                                    JsonSupport.stringMember("file", model.file().toString())))))));
    sendBytes(
        exchange, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * {@code GET /api/process-map/model?process=<processId>} -- the BPMN XML plus its element/
   * sequence-flow index.
   */
  private void handleProcessMapModel(final HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendPlainText(exchange, 405, "Method Not Allowed");
      return;
    }
    final Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
    final String processId = params.get("process");
    if (processId == null || processId.isBlank()) {
      sendPlainText(exchange, 400, "Missing required 'process' query parameter");
      return;
    }
    final BpmnCatalog.ScanResult scan = BpmnCatalog.discover(bpmnDirOverride);
    final Optional<BpmnCatalog.BpmnModel> model =
        scan.models().stream().filter(m -> m.processId().equals(processId)).findFirst();
    if (model.isEmpty()) {
      sendJson(exchange, jsonErrorObject("No BPMN model found for process id " + processId));
      return;
    }
    ensureAllCannedViews();
    final BpmnCatalog.BpmnModel bpmnModel = model.get();
    final String bpmnXml;
    try {
      bpmnXml = Files.readString(bpmnModel.file(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      sendJson(exchange, jsonErrorObject("Failed to read BPMN file: " + e.getMessage()));
      return;
    }
    final String json =
        JsonSupport.object(
            List.of(
                JsonSupport.stringMember("processId", bpmnModel.processId()),
                JsonSupport.stringMember(
                    "processName",
                    bpmnModel.processName().isBlank()
                        ? bpmnModel.processId()
                        : bpmnModel.processName()),
                JsonSupport.stringMember("bpmnXml", bpmnXml),
                JsonSupport.member(
                    "elements",
                    JsonSupport.arrayOf(
                        List.copyOf(bpmnModel.elementsById().values()),
                        el ->
                            JsonSupport.object(
                                List.of(
                                    JsonSupport.stringMember("id", el.id()),
                                    JsonSupport.stringMember("name", el.name()),
                                    JsonSupport.stringMember("type", el.type()))))),
                JsonSupport.member(
                    "sequenceFlows",
                    JsonSupport.arrayOf(
                        bpmnModel.sequenceFlows(),
                        flow ->
                            JsonSupport.object(
                                List.of(
                                    JsonSupport.stringMember("id", flow.id()),
                                    JsonSupport.stringMember("sourceRef", flow.sourceRef()),
                                    JsonSupport.stringMember("targetRef", flow.targetRef())))))));
    sendJson(exchange, json);
  }

  /**
   * {@code GET /api/process-map?process=<processId>} -- node badges (execution count + avg
   * duration) and edge stats (n + avg gap), both over every instance.
   */
  private void handleProcessMapData(final HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      sendPlainText(exchange, 405, "Method Not Allowed");
      return;
    }
    final Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
    final String processId = params.get("process");
    if (processId == null || processId.isBlank()) {
      sendPlainText(exchange, 400, "Missing required 'process' query parameter");
      return;
    }

    ensureAllCannedViews();
    if (!viewExists(ACTIVITIES_VIEW)) {
      sendJson(
          exchange,
          jsonErrorObject(
              "No activities data yet for process-map -- run the translator against this "
                  + "warehouse first."));
      return;
    }

    try {
      final List<ProcessMapService.NodeStat> nodes =
          ProcessMapService.nodeStats(duckdb, ACTIVITIES_VIEW, processId);

      final String json =
          JsonSupport.object(
              List.of(
                  JsonSupport.stringMember("process", processId),
                  JsonSupport.member(
                      "nodes",
                      JsonSupport.arrayOf(
                          nodes,
                          n ->
                              JsonSupport.object(
                                  List.of(
                                      JsonSupport.stringMember("elementId", n.elementId()),
                                      JsonSupport.numberMember(
                                          "executionCount", n.executionCount()),
                                      JsonSupport.numberMember(
                                          "avgDurationMs", n.avgDurationMs())))))));
      sendJson(exchange, json);
    } catch (final SQLException e) {
      sendJson(exchange, jsonErrorObject(e.getMessage()));
    }
  }

  private static Map<String, String> parseQueryParams(final String rawQuery) {
    final Map<String, String> params = new LinkedHashMap<>();
    if (rawQuery == null || rawQuery.isBlank()) {
      return params;
    }
    for (final String pair : rawQuery.split("&")) {
      final int eq = pair.indexOf('=');
      final String key = eq < 0 ? pair : pair.substring(0, eq);
      final String value = eq < 0 ? "" : pair.substring(eq + 1);
      params.put(
          URLDecoder.decode(key, StandardCharsets.UTF_8),
          URLDecoder.decode(value, StandardCharsets.UTF_8));
    }
    return params;
  }

  private static String jsonErrorObject(final String message) {
    return JsonSupport.object(
        List.of(JsonSupport.stringMember("error", message == null ? "unknown error" : message)));
  }

  private void sendJson(final HttpExchange exchange, final String json) throws IOException {
    sendBytes(
        exchange, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
  }

  // ---------------------------------------------------------------------------------------------
  // Query execution
  // ---------------------------------------------------------------------------------------------

  /**
   * (Re)creates whichever of the four canned views have data available, then executes {@code sql}.
   * Every failure path here -- a still-missing view referenced by the query, a genuine SQL error,
   * whatever -- resolves to a {@code {"error": ...}} JSON body, never an HTTP 500; that's the whole
   * point of this method existing as a single guarded entry point.
   */
  /**
   * (Re)creates every canned view this server knows how to serve -- the two metadata-driven raw
   * views and the two single-file open-state views -- skipping whichever aren't available yet.
   * Shared by {@link #runGuarded} (the {@code /api/query}/dashboard path) and the {@code
   * /process-map} handlers below, so both paths see exactly the same view set with no duplicated
   * wiring.
   */
  private void ensureAllCannedViews() {
    ensureCatalogAndTablesLoaded();
    ensureIcebergBackedView(INSTANCES_VIEW, instancesTable);
    ensureIcebergBackedView(ACTIVITIES_VIEW, activitiesTable);
    ensureViewIfAvailable(
        OPEN_INSTANCES_VIEW,
        Files.isRegularFile(openInstancesFile),
        singleFileGlob(openInstancesFile));
    ensureViewIfAvailable(
        OPEN_ELEMENTS_VIEW,
        Files.isRegularFile(openElementsFile),
        singleFileGlob(openElementsFile));
  }

  private String runGuarded(final String sql) {
    ensureAllCannedViews();

    // Friendly short-circuit: if the query textually references a view whose data isn't there
    // yet, say so plainly rather than surfacing DuckDB's raw "table does not exist" message (which
    // reads like a typo, not "the translator hasn't produced this table's data yet").
    final String sqlLower = sql.toLowerCase(Locale.ROOT);
    for (final String view : CANNED_VIEWS) {
      if (!viewExists(view) && mentionsIdentifier(sqlLower, view)) {
        return jsonError(
            "No data yet for '"
                + view
                + "' -- the translator hasn't produced any rows for it yet. Start it against a "
                + "live stack (see the module README) and try again once it has flushed.");
      }
    }

    try (Statement statement = duckdb.createStatement()) {
      try {
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
      } catch (final SQLException ignored) {
        // Not every JDBC driver build supports setQueryTimeout; best-effort only.
      }
      final boolean hasResultSet = statement.execute(sql);
      if (!hasResultSet) {
        // A DDL/DML statement with no result set (e.g. someone pasted a CREATE TABLE into the
        // free-form box) -- report success with an empty shape rather than an error.
        return jsonResult(List.of(), List.of());
      }
      try (ResultSet resultSet = statement.getResultSet()) {
        return jsonResult(readColumns(resultSet), readRows(resultSet));
      }
    } catch (final SQLException e) {
      return jsonError(e.getMessage());
    }
  }

  private boolean viewExists(final String viewName) {
    try (Statement statement = duckdb.createStatement()) {
      statement.execute(
          "SELECT 1 FROM information_schema.tables WHERE table_name = '"
              + escapeSqlLiteral(viewName)
              + "'");
      try (ResultSet rs = statement.getResultSet()) {
        return rs.next();
      }
    } catch (final SQLException e) {
      return false;
    }
  }

  private static boolean mentionsIdentifier(final String sqlLower, final String identifier) {
    return Pattern.compile("\\b" + Pattern.quote(identifier) + "\\b").matcher(sqlLower).find();
  }

  private void ensureViewIfAvailable(
      final String viewName, final boolean dataAvailable, final String parquetGlob) {
    if (!dataAvailable) {
      return;
    }
    try (Statement statement = duckdb.createStatement()) {
      statement.execute(
          "CREATE OR REPLACE VIEW "
              + viewName
              + " AS SELECT * FROM read_parquet('"
              + escapeSqlLiteral(parquetGlob)
              + "')");
    } catch (final SQLException e) {
      // Data appeared to exist a moment ago (file-existence check) but the view bind still failed
      // -- e.g. a flush is mid-write. Leave the view undefined for this request; the caller's
      // "does it exist" check a few lines up will then produce the friendly no-data-yet message.
      LOG.debug("Could not (re)create view {}: {}", viewName, e.getMessage());
    }
  }

  private static String singleFileGlob(final Path file) {
    return file.toAbsolutePath().toString();
  }

  // ---------------------------------------------------------------------------------------------
  // Metadata-driven instances/activities views -- this server's own Iceberg catalog handle.
  // ---------------------------------------------------------------------------------------------

  /**
   * (Re)loads {@link #catalog}/{@link #instancesTable}/{@link #activitiesTable} if not already
   * loaded. Safe to call on every request: once a {@code Table} handle has been obtained it is kept
   * (refreshed on read, never reopened), and while the translator hasn't created the
   * warehouse/tables yet this simply no-ops and retries on the next request -- mirroring the
   * existing "no data yet" tolerance the other two (single-file) views already have.
   *
   * <p>{@code synchronized}, along with {@link #ensureIcebergBackedView}: the dashboard page fires
   * several {@code /api/query} requests in parallel (one per tile), which this server's 4-thread
   * request-executor pool runs concurrently -- without a lock, two of those requests could call
   * {@link Table#refresh()} on the very same shared {@code Table} instance at once, which
   * iceberg-core does not guarantee is safe.
   */
  private synchronized void ensureCatalogAndTablesLoaded() {
    if (instancesTable != null && activitiesTable != null) {
      return;
    }
    if (catalog == null) {
      if (!Files.isDirectory(warehouseDir)) {
        // The translator hasn't even created the warehouse directory yet. Deliberately not
        // creating it ourselves here -- this is a read-only UI, and doing so would be a surprising
        // side effect of just loading a page before the translator has started.
        return;
      }
      try {
        catalog = openCatalog(warehouseDir);
      } catch (final RuntimeException e) {
        LOG.debug("Could not open the Iceberg catalog yet at {}: {}", warehouseDir, e.getMessage());
        return;
      }
    }
    final Namespace namespace = Namespace.of("lake");
    if (instancesTable == null) {
      instancesTable = loadTableIfExists(namespace, INSTANCES_VIEW);
    }
    if (activitiesTable == null) {
      activitiesTable = loadTableIfExists(namespace, ACTIVITIES_VIEW);
    }
  }

  private Table loadTableIfExists(final Namespace namespace, final String tableName) {
    final TableIdentifier identifier = TableIdentifier.of(namespace, tableName);
    try {
      return catalog.tableExists(identifier) ? catalog.loadTable(identifier) : null;
    } catch (final RuntimeException e) {
      LOG.debug("Could not load table {} yet: {}", identifier, e.getMessage());
      return null;
    }
  }

  /**
   * Opens a fresh {@link JdbcCatalog} against the same H2 catalog/warehouse location {@link
   * io.camunda.analytics.lake.write.IcebergLakeWriter} constructs its own catalog against — see
   * that class's constructor for the source of truth this mirrors. This is a deliberately SEPARATE
   * catalog instance from the writer's (see this class's javadoc for why sharing would be unsafe);
   * H2 in embedded mode already supports multiple connections to one database file from within a
   * single JVM, which is what makes a second, independent handle here safe.
   */
  private static JdbcCatalog openCatalog(final Path warehouseDir) {
    final JdbcCatalog catalog = new JdbcCatalog(properties -> new LocalFileIO(), null, true);
    final String h2Url = "jdbc:h2:file:" + warehouseDir.toAbsolutePath().resolve("catalog");
    final String warehouseLocation = warehouseFileUri(warehouseDir);
    catalog.initialize(
        "lake",
        Map.of(
            CatalogProperties.URI, h2Url,
            CatalogProperties.WAREHOUSE_LOCATION, warehouseLocation));
    return catalog;
  }

  /** {@code file:} URI form of a local directory, with any trailing slash stripped. */
  private static String warehouseFileUri(final Path warehouseDir) {
    final String uri = warehouseDir.toAbsolutePath().toUri().toString();
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  /**
   * (Re)creates {@code viewName} as {@code SELECT * FROM read_parquet([...])} over {@code table}'s
   * CURRENT snapshot's data files, or leaves the view undefined if {@code table} is {@code null}
   * (not loaded yet) or the current snapshot has no data files.
   *
   * <h2>Why metadata-driven, not a directory glob</h2>
   *
   * <p>The old implementation pointed {@code read_parquet} at a directory glob (every {@code
   * *.parquet} file physically present under the table's {@code data/} directory). That is wrong
   * once {@link io.camunda.analytics.lake.write.LakeCompactor} is in the picture: compaction
   * rewrites a table's data files into one compacted file but retains the last 3 snapshots, so a
   * just-superseded file legitimately stays on disk (still referenced by an older, retained
   * snapshot) until a later {@code expireSnapshots} pass removes it -- and a crash between a
   * compaction rewrite's Parquet write and its Iceberg commit leaves an orphaned file that is never
   * referenced by any snapshot at all. A directory glob can't distinguish either of those from a
   * live file: it reads the old file AND its replacement, double-counting every row. Deriving the
   * file list from {@code table.newScan().planFiles()} instead (see {@link
   * #currentDataFilePaths(Table)}) reads exactly what the CURRENT snapshot says is live -- which is
   * what "snapshot-consistent" means here.
   *
   * <p>Not cached across requests -- rebuilt on every call, like the other views (see class
   * javadoc's "Lazy, per-request views" section). Correctness first; this is a demo. A production
   * version could reuse the view across requests by checking whether {@link
   * Table#currentSnapshot()}'s id changed since the last rebuild.
   *
   * <p>{@code synchronized}: see {@link #ensureCatalogAndTablesLoaded()}'s javadoc -- {@code table}
   * is one of this instance's shared, mutable {@link #instancesTable}/{@link #activitiesTable}
   * handles, and {@link #currentDataFilePaths(Table)} calls {@link Table#refresh()} on it.
   */
  private synchronized void ensureIcebergBackedView(final String viewName, final Table table) {
    if (table == null) {
      return;
    }
    final List<Path> files = currentDataFilePaths(table);
    if (files.isEmpty()) {
      return;
    }
    final String fileList =
        files.stream()
            .map(path -> "'" + escapeSqlLiteral(path.toString()) + "'")
            .collect(Collectors.joining(", "));
    try (Statement statement = duckdb.createStatement()) {
      statement.execute(
          "CREATE OR REPLACE VIEW "
              + viewName
              + " AS SELECT * FROM read_parquet(["
              + fileList
              + "])");
    } catch (final SQLException e) {
      // Same tolerance as the single-file views below: leave the view undefined for this request
      // and let the caller's "does it exist" check produce the friendly no-data-yet message.
      LOG.debug("Could not (re)create view {}: {}", viewName, e.getMessage());
    }
  }

  /**
   * Snapshot-consistent list of {@code table}'s current data-file paths: refreshes the table
   * handle, plans the current snapshot's files, dedupes by {@link
   * org.apache.iceberg.DataFile#location()} (one physical data file can be split into several
   * scan-task byte ranges -- see {@code LakeCompactor#currentDataFiles} for the same idiom), and
   * normalizes each location to a plain filesystem path via {@link LocalFileIO#toFilesystemPath} so
   * DuckDB's {@code read_parquet} can open it directly.
   *
   * <p>Package-private (not {@code private}) so a test can call it directly against real writer/
   * compactor output without spinning up a full {@link LakeUiServer}.
   */
  static List<Path> currentDataFilePaths(final Table table) {
    table.refresh();
    final LinkedHashSet<String> locations = new LinkedHashSet<>();
    try (CloseableIterable<FileScanTask> tasks = table.newScan().planFiles()) {
      for (final FileScanTask task : tasks) {
        locations.add(task.file().location());
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to plan data files for " + table.name(), e);
    }
    return locations.stream().map(LocalFileIO::toFilesystemPath).toList();
  }

  // ---------------------------------------------------------------------------------------------
  // Single-file open-state views -- unchanged: StateSnapshotDumper atomically replaces these, so
  // globbing a single fixed path is already correct (no compaction-retained old copy is possible).
  // Availability is checked directly with Files.isRegularFile at the runGuarded call site above.
  // ---------------------------------------------------------------------------------------------

  private static List<String> readColumns(final ResultSet resultSet) throws SQLException {
    final ResultSetMetaData meta = resultSet.getMetaData();
    final List<String> columns = new ArrayList<>(meta.getColumnCount());
    for (int i = 1; i <= meta.getColumnCount(); i++) {
      columns.add(meta.getColumnLabel(i));
    }
    return columns;
  }

  private static List<List<Object>> readRows(final ResultSet resultSet) throws SQLException {
    final int columnCount = resultSet.getMetaData().getColumnCount();
    final List<List<Object>> rows = new ArrayList<>();
    while (rows.size() < MAX_ROWS && resultSet.next()) {
      final List<Object> row = new ArrayList<>(columnCount);
      for (int i = 1; i <= columnCount; i++) {
        row.add(resultSet.getObject(i));
      }
      rows.add(row);
    }
    return rows;
  }

  private static String escapeSqlLiteral(final String value) {
    return value.replace("'", "''");
  }

  // ---------------------------------------------------------------------------------------------
  // Hand-rolled JSON encoding -- deliberately no JSON library dependency for this PoC UI.
  // ---------------------------------------------------------------------------------------------

  private static String jsonResult(final List<String> columns, final List<List<Object>> rows) {
    final StringBuilder json = new StringBuilder();
    json.append("{\"columns\":[");
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      appendJsonString(json, columns.get(i));
    }
    json.append("],\"rows\":[");
    for (int r = 0; r < rows.size(); r++) {
      if (r > 0) {
        json.append(',');
      }
      json.append('[');
      final List<Object> row = rows.get(r);
      for (int c = 0; c < row.size(); c++) {
        if (c > 0) {
          json.append(',');
        }
        appendJsonValue(json, row.get(c));
      }
      json.append(']');
    }
    json.append("]}");
    return json.toString();
  }

  private static String jsonError(final String message) {
    final StringBuilder json = new StringBuilder();
    json.append("{\"error\":");
    appendJsonString(json, message == null ? "unknown error" : message);
    json.append('}');
    return json.toString();
  }

  /**
   * Numbers are emitted as JSON numbers; everything else via {@code String.valueOf}; nulls as null.
   */
  private static void appendJsonValue(final StringBuilder json, final Object value) {
    if (value == null) {
      json.append("null");
      return;
    }
    if (value instanceof final Number number) {
      final double asDouble = number.doubleValue();
      if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
        // Not valid JSON literals -- fall back to a quoted string rather than emitting broken JSON.
        appendJsonString(json, number.toString());
      } else {
        json.append(number);
      }
      return;
    }
    appendJsonString(json, String.valueOf(value));
  }

  private static void appendJsonString(final StringBuilder json, final String value) {
    json.append('"');
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      switch (c) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> {
          if (c < 0x20) {
            json.append(String.format("\\u%04x", (int) c));
          } else {
            json.append(c);
          }
        }
      }
    }
    json.append('"');
  }

  // ---------------------------------------------------------------------------------------------
  // Low-level HTTP plumbing
  // ---------------------------------------------------------------------------------------------

  private static void sendBytes(
      final HttpExchange exchange, final int status, final String contentType, final byte[] body)
      throws IOException {
    exchange.getResponseHeaders().set("Content-Type", contentType);
    exchange.sendResponseHeaders(status, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private static void sendPlainText(
      final HttpExchange exchange, final int status, final String body) throws IOException {
    sendBytes(exchange, status, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
  }

  // ---------------------------------------------------------------------------------------------
  // The page itself: one self-contained HTML document, no external assets.
  // ---------------------------------------------------------------------------------------------

  private static final String INDEX_HTML =
      """
      <!doctype html>
      <html lang="en">
      <head>
      <meta charset="utf-8">
      <title>Analytics Lake PoC</title>
      <style>
        :root {
          color-scheme: light dark;
          --bg: #f5f6f8;
          --fg: #1b1f24;
          --card-bg: #ffffff;
          --border: #d8dce1;
          --accent: #2b6cb0;
          --muted: #6b7280;
          --error: #b91c1c;
        }
        @media (prefers-color-scheme: dark) {
          :root {
            --bg: #14171c;
            --fg: #e6e8eb;
            --card-bg: #1c2027;
            --border: #333a45;
            --accent: #6ea8fe;
            --muted: #9aa4b2;
            --error: #ff8787;
          }
        }
        * { box-sizing: border-box; }
        body {
          margin: 0;
          padding: 1.5rem;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          background: var(--bg);
          color: var(--fg);
        }
        h1 { margin: 0 0 0.25rem; font-size: 1.4rem; }
        p.subtitle { margin: 0 0 1.5rem; color: var(--muted); font-size: 0.9rem; }
        p.subtitle a { color: var(--accent); }
        .grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
          gap: 1rem;
          margin-bottom: 1.5rem;
        }
        .card {
          background: var(--card-bg);
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 1rem;
        }
        .card h2 { margin: 0 0 0.5rem; font-size: 1rem; }
        .card p.desc { margin: 0 0 0.75rem; color: var(--muted); font-size: 0.85rem; }
        button {
          background: var(--accent);
          color: #fff;
          border: none;
          border-radius: 6px;
          padding: 0.45rem 0.9rem;
          cursor: pointer;
          font-size: 0.85rem;
        }
        button:hover { opacity: 0.9; }
        .result-wrap { margin-top: 0.75rem; max-height: 340px; overflow: auto; }
        table { border-collapse: collapse; width: 100%; font-size: 0.8rem; }
        th, td {
          border: 1px solid var(--border);
          padding: 0.3rem 0.5rem;
          text-align: left;
          white-space: nowrap;
        }
        th { position: sticky; top: 0; background: var(--card-bg); }
        .error { color: var(--error); font-size: 0.85rem; white-space: pre-wrap; }
        .freeform textarea {
          width: 100%;
          min-height: 110px;
          font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
          font-size: 0.85rem;
          background: var(--card-bg);
          color: var(--fg);
          border: 1px solid var(--border);
          border-radius: 6px;
          padding: 0.6rem;
        }
        .freeform { background: var(--card-bg); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
        .freeform h2 { margin: 0 0 0.5rem; font-size: 1rem; }
      </style>
      </head>
      <body>
      <h1>Analytics Lake PoC</h1>
      <p class="subtitle">Local demo UI over the Iceberg lake tables and the open-state snapshot -- read-only, DuckDB-backed, no auth.
        &nbsp;<a href="/dashboard">Open the chart dashboard &rarr;</a>
        &nbsp;<a href="/process-map">Open the process map &rarr;</a></p>

      <div class="grid" id="tiles"></div>

      <div class="freeform">
        <h2>Free-form SQL</h2>
        <textarea id="freeform-sql" spellcheck="false">SELECT * FROM instances LIMIT 20</textarea>
        <div style="margin-top:0.5rem;">
          <button id="freeform-run">Run</button>
        </div>
        <div class="result-wrap" id="freeform-result"></div>
      </div>

      <script>
        // Each tile's canned query, kept in one place so the HTML below just references a name.
        const TILES = [
          {
            id: 'instances-per-process',
            title: 'Instances per process',
            desc: 'Count, average and p95 duration per process/version.',
            sql: `SELECT process_id, version, count(*) AS instance_count,
                         avg(duration_ms) AS avg_duration_ms,
                         quantile_cont(duration_ms, 0.95) AS p95_duration_ms
                  FROM instances
                  GROUP BY process_id, version
                  ORDER BY instance_count DESC`,
          },
          {
            id: 'activities-per-element',
            title: 'Activities per element',
            desc: 'Count and average duration per BPMN element.',
            sql: `SELECT process_id, element_id, count(*) AS activity_count,
                         avg(duration_ms) AS avg_duration_ms
                  FROM activities
                  GROUP BY process_id, element_id
                  ORDER BY activity_count DESC`,
          },
          {
            id: 'bottleneck-edges',
            title: 'Bottleneck edges',
            desc: 'Average gap between consecutive elements in the same instance.',
            sql: `WITH ordered AS (
                    SELECT instance_key, element_id, started_at, ended_at,
                           lead(element_id) OVER (PARTITION BY instance_key ORDER BY started_at) AS next_element_id,
                           lead(started_at) OVER (PARTITION BY instance_key ORDER BY started_at) AS next_started_at
                    FROM activities
                  )
                  SELECT element_id AS from_element, next_element_id AS to_element,
                         count(*) AS transitions,
                         avg(epoch_ms(next_started_at) - epoch_ms(ended_at)) AS avg_gap_ms
                  FROM ordered
                  WHERE next_element_id IS NOT NULL
                  GROUP BY 1, 2
                  ORDER BY avg_gap_ms DESC
                  LIMIT 20`,
          },
          {
            id: 'currently-running',
            title: 'Currently running per process',
            desc: 'From the periodic open-state snapshot, not the finished-row tables.',
            sql: `SELECT process_id, version, count(*) AS running_count,
                         to_timestamp(min(start_ms) / 1000) AS oldest_start
                  FROM open_instances
                  GROUP BY process_id, version
                  ORDER BY running_count DESC`,
          },
          {
            id: 'recent-instances',
            title: 'Recent instances',
            desc: 'Last 20 finished instances by end time.',
            sql: `SELECT key, process_id, version, state, duration_ms,
                         ended_at,
                         substr(vars_json, 1, 200) AS vars_json_preview
                  FROM instances
                  ORDER BY ended_at DESC
                  LIMIT 20`,
          },
        ];

        function escapeHtml(s) {
          const div = document.createElement('div');
          div.textContent = s;
          return div.innerHTML;
        }

        function renderResult(container, json) {
          if (json.error) {
            container.innerHTML = '<div class="error">' + escapeHtml(json.error) + '</div>';
            return;
          }
          if (!json.columns || json.columns.length === 0) {
            container.innerHTML = '<div class="error">(no columns / statement had no result set)</div>';
            return;
          }
          let html = '<table><thead><tr>';
          for (const col of json.columns) {
            html += '<th>' + escapeHtml(col) + '</th>';
          }
          html += '</tr></thead><tbody>';
          for (const row of json.rows) {
            html += '<tr>';
            for (const value of row) {
              html += '<td>' + (value === null ? '<em>null</em>' : escapeHtml(String(value))) + '</td>';
            }
            html += '</tr>';
          }
          html += '</tbody></table>';
          if (json.rows.length === 0) {
            html += '<div class="error">(0 rows)</div>';
          }
          container.innerHTML = html;
        }

        async function runQuery(sql, container) {
          container.innerHTML = '<div>Running...</div>';
          try {
            const response = await fetch('/api/query', { method: 'POST', body: sql });
            const json = await response.json();
            renderResult(container, json);
          } catch (e) {
            container.innerHTML = '<div class="error">' + escapeHtml(String(e)) + '</div>';
          }
        }

        const tilesEl = document.getElementById('tiles');
        for (const tile of TILES) {
          const card = document.createElement('div');
          card.className = 'card';
          card.innerHTML =
            '<h2>' + escapeHtml(tile.title) + '</h2>' +
            '<p class="desc">' + escapeHtml(tile.desc) + '</p>' +
            '<button>Run</button>' +
            '<div class="result-wrap"></div>';
          const button = card.querySelector('button');
          const resultWrap = card.querySelector('.result-wrap');
          button.addEventListener('click', () => runQuery(tile.sql, resultWrap));
          tilesEl.appendChild(card);
        }

        document.getElementById('freeform-run').addEventListener('click', () => {
          const sql = document.getElementById('freeform-sql').value;
          runQuery(sql, document.getElementById('freeform-result'));
        });
      </script>
      </body>
      </html>
      """;

  /**
   * Defeats {@code javac}'s compile-time constant folding for the {@code DASHBOARD_HTML_PART*}
   * split (see {@link #DASHBOARD_HTML_PART2}'s javadoc): a field initialized with a plain string
   * literal is a "constant variable" (JLS 4.12.4), and {@code A + B} of two constant variables is
   * itself folded into a single constant at compile time -- which would recreate the exact
   * over-65535-byte constant-pool entry the split was meant to avoid, even though the fold happens
   * in a different field. Routing each half's literal through this trivial identity method means
   * neither {@code PART} field's initializer is a constant expression, so {@code javac} is forced
   * to concatenate them as ordinary bytecode at class-init time instead.
   */
  private static String asIs(final String s) {
    return s;
  }

  /**
   * The chart dashboard: self-contained SVG/vanilla-JS tiles over the same {@code /api/query}
   * endpoint as {@link #INDEX_HTML}, auto-refreshing every 10 seconds (paused while the tab is
   * hidden). See the module's dataviz design notes for the palette and mark choices.
   *
   * <p>First half of the page source -- split into {@link #DASHBOARD_HTML_PART1}/{@link
   * #DASHBOARD_HTML_PART2} purely because a single text block over ~64KB trips {@code javac}'s
   * constant-pool string-length limit; see {@link #DASHBOARD_HTML_PART2}'s javadoc.
   */
  private static final String DASHBOARD_HTML_PART1 =
      asIs(
          """
      <!doctype html>
      <html lang="en">
      <head>
      <meta charset="utf-8">
      <title>Analytics Lake Dashboard</title>
      <style>
        :root {
          color-scheme: light dark;
          --page-bg: #f9f9f7;
          --surface: #fcfcfb;
          --ink-primary: #0b0b0b;
          --ink-secondary: #52514e;
          --ink-muted: #898781;
          --gridline: #e1e0d9;
          --baseline: #c3c2b7;
          --border: rgba(11,11,11,0.10);
          --error: #d03b3b;
          --series-1: #2a78d6;
          --series-1-wash: rgba(42,120,214,0.10);
          --series-2: #008300;
        }
        @media (prefers-color-scheme: dark) {
          :root {
            --page-bg: #0d0d0d;
            --surface: #1a1a19;
            --ink-primary: #ffffff;
            --ink-secondary: #c3c2b7;
            --ink-muted: #898781;
            --gridline: #2c2c2a;
            --baseline: #383835;
            --border: rgba(255,255,255,0.10);
            --error: #e66767;
            --series-1: #3987e5;
            --series-1-wash: rgba(57,135,229,0.12);
            --series-2: #008300;
          }
        }
        * { box-sizing: border-box; }
        body {
          margin: 0;
          padding: 1.5rem;
          font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
          background: var(--page-bg);
          color: var(--ink-primary);
          overflow-x: hidden;
        }
        h1 { margin: 0 0 0.25rem; font-size: 1.4rem; }
        p.subtitle { margin: 0 0 1.5rem; color: var(--ink-muted); font-size: 0.9rem; }
        p.subtitle a { color: var(--series-1); }
        .refresh-state { font-size: 0.75rem; color: var(--ink-muted); }

        .grid {
          display: grid;
          grid-template-columns: repeat(3, minmax(0, 1fr));
          gap: 1rem;
          max-width: 1400px;
        }
        .tile-stats { grid-column: 1 / -1; }
        .tile-line { grid-column: span 2; }
        .tile-bars, .tile-pctl, .tile-region, .tile-spread { grid-column: span 1; }
        .tile-duration { grid-column: 1 / -1; }
        @media (max-width: 900px) {
          .grid { grid-template-columns: 1fr; }
          .tile-line { grid-column: span 1; }
        }

        .card {
          background: var(--surface);
          border: 1px solid var(--border);
          border-radius: 8px;
          padding: 1rem;
          min-width: 0;
          transition: opacity 0.15s ease;
        }
        .card.is-loading { opacity: 0.55; }
        .card h2 { margin: 0 0 0.15rem; font-size: 0.95rem; }
        .card p.caption { margin: 0 0 0.75rem; color: var(--ink-muted); font-size: 0.78rem; }
        .card .tile-error {
          color: var(--error);
          font-size: 0.82rem;
          padding: 0.5rem 0;
          display: none;
        }
        .card .tile-error.visible { display: block; }
        .card .chart-body.hidden { display: none; }

        /* Tile 1: stat row */
        .stat-row {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
          gap: 1.25rem;
        }
        .stat .stat-label { margin: 0 0 0.2rem; font-size: 0.78rem; color: var(--ink-secondary); }
        .stat .stat-value {
          font-size: 2rem;
          font-weight: 600;
          color: var(--ink-primary);
          line-height: 1.15;
          font-variant-numeric: normal;
        }

        svg.chart-svg { width: 100%; height: auto; display: block; overflow: visible; }
        svg.chart-svg text {
          fill: var(--ink-muted);
          font-size: 11px;
          font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
          font-variant-numeric: tabular-nums;
        }
        svg.chart-svg text.value-label { fill: var(--ink-secondary); font-weight: 600; }
        svg.chart-svg text.category-label { fill: var(--ink-primary); font-variant-numeric: normal; }
        svg.chart-svg .grid-line { stroke: var(--gridline); stroke-width: 1; }
        svg.chart-svg .baseline { stroke: var(--baseline); stroke-width: 1; }
        svg.chart-svg .hit-rect { fill: transparent; cursor: pointer; }
        svg.chart-svg .hit-rect:focus { outline: none; }
        svg.chart-svg .bar-mark { transition: filter 0.1s ease; }
        svg.chart-svg .bar-mark.hovered { filter: brightness(1.12); }

        .legend { display: flex; gap: 1rem; margin-bottom: 0.5rem; font-size: 0.78rem; color: var(--ink-secondary); }
        .legend-item { display: flex; align-items: center; gap: 0.35rem; }
        .legend-key { width: 14px; height: 2px; border-radius: 1px; display: inline-block; }

        /* Tile 7 controls: time-range toggle + process <select>, both re-fetch immediately on
           change (see the tile's own JS section) rather than waiting for the next auto-refresh. */
        .tile-controls { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.75rem; flex-wrap: wrap; }
        .range-toggle { display: flex; gap: 0.25rem; }
        .range-toggle button {
          background: transparent;
          border: 1px solid var(--border);
          color: var(--ink-secondary);
          border-radius: 6px;
          padding: 0.3rem 0.65rem;
          font-size: 0.78rem;
          font-family: inherit;
          cursor: pointer;
        }
        .range-toggle button:hover { border-color: var(--series-1); color: var(--ink-primary); }
        .range-toggle button.active { background: var(--series-1); border-color: var(--series-1); color: #fff; }
        .tile-controls select {
          background: var(--surface);
          color: var(--ink-primary);
          border: 1px solid var(--border);
          border-radius: 6px;
          padding: 0.3rem 0.5rem;
          font-size: 0.78rem;
          font-family: inherit;
        }

        details.data-table { margin-top: 0.6rem; }
        details.data-table summary {
          cursor: pointer;
          font-size: 0.75rem;
          color: var(--ink-muted);
          user-select: none;
        }
        details.data-table table {
          border-collapse: collapse;
          width: 100%;
          font-size: 0.75rem;
          margin-top: 0.4rem;
          font-variant-numeric: tabular-nums;
        }
        details.data-table th, details.data-table td {
          border: 1px solid var(--border);
          padding: 0.25rem 0.4rem;
          text-align: left;
          white-space: nowrap;
        }

        .chart-tooltip {
          position: fixed;
          display: none;
          pointer-events: none;
          background: var(--surface);
          border: 1px solid var(--border);
          border-radius: 6px;
          padding: 0.4rem 0.6rem;
          font-size: 0.78rem;
          color: var(--ink-primary);
          box-shadow: 0 2px 10px rgba(0,0,0,0.18);
          z-index: 100;
          font-variant-numeric: tabular-nums;
          max-width: 240px;
        }
        .chart-tooltip .tt-title { color: var(--ink-secondary); margin-bottom: 0.15rem; }
        .chart-tooltip .tt-row { display: flex; align-items: center; gap: 0.35rem; }
        .chart-tooltip .tt-key { width: 10px; height: 2px; display: inline-block; border-radius: 1px; }
        .chart-tooltip .tt-value { font-weight: 600; }
        /* Interactive tooltips (tile 6): pointer-events stay off by default (see .chart-tooltip
           above) so a tooltip never blocks hovering the chart underneath it; this class turns them
           back on only for tooltips that carry a clickable exemplar key. */
        .chart-tooltip.interactive { pointer-events: auto; }
        .chart-tooltip .tt-exemplar {
          margin-top: 0.3rem;
          padding-top: 0.3rem;
          border-top: 1px solid var(--border);
        }
        .chart-tooltip .tt-key-code {
          font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
          background: var(--gridline);
          border-radius: 4px;
          padding: 0.05rem 0.4rem;
          cursor: pointer;
        }
        .chart-tooltip .tt-key-code:hover { filter: brightness(1.15); }
        .chart-tooltip .tt-key-code.copied { color: var(--series-1); }
      </style>
      </head>
      <body>
      <h1>Analytics Lake Dashboard</h1>
      <p class="subtitle">
        Live view over the same lake data as the data explorer.
        <a href="/">&larr; Back to the data explorer</a>
        &nbsp;&middot;&nbsp;<a href="/process-map">Open the process map &rarr;</a>
        &nbsp;&middot;&nbsp;<span class="refresh-state" id="refresh-state">refreshing every 10s</span>
      </p>

      <div class="grid">

        <div class="card tile-stats" id="tile-stats">
          <h2>Overview</h2>
          <div class="tile-error" id="err-stats"></div>
          <div class="stat-row chart-body" id="body-stats">
            <div class="stat">
              <p class="stat-label">Total completed instances</p>
              <p class="stat-value" id="stat-total">&mdash;</p>
            </div>
            <div class="stat">
              <p class="stat-label">Running right now</p>
              <p class="stat-value" id="stat-running">&mdash;</p>
            </div>
            <div class="stat">
              <p class="stat-label">Completions, last 5 min</p>
              <p class="stat-value" id="stat-recent">&mdash;</p>
            </div>
            <div class="stat">
              <p class="stat-label">Overall p95 duration</p>
              <p class="stat-value" id="stat-p95">&mdash;</p>
            </div>
          </div>
        </div>

        <div class="card tile-line" id="tile-line">
          <h2>Completions per minute</h2>
          <p class="caption">Last 15 minutes.</p>
          <div class="tile-error" id="err-line"></div>
          <div class="chart-body" id="body-line"></div>
        </div>

        <div class="card tile-bars" id="tile-bars">
          <h2>Instances per process</h2>
          <p class="caption">Completed instance count, sorted descending.</p>
          <div class="tile-error" id="err-bars"></div>
          <div class="chart-body" id="body-bars"></div>
        </div>

        <div class="card tile-pctl" id="tile-pctl">
          <h2>Duration percentiles per process</h2>
          <p class="caption">Typical (p50) vs. worst-case (p95) duration.</p>
          <div class="tile-error" id="err-pctl"></div>
          <div class="chart-body" id="body-pctl"></div>
        </div>

        <div class="card tile-region" id="tile-region">
          <h2>Where instances run: region split</h2>
          <p class="caption">Ad hoc: pulled straight out of the schema-free <code>vars_json</code> column, no new column needed.</p>
          <div class="tile-error" id="err-region"></div>
          <div class="chart-body" id="body-region"></div>
        </div>

        <div class="card tile-spread" id="tile-spread">
          <h2>Duration spread per process</h2>
          <p class="caption">Min &middot; median &middot; p95 &middot; max, with the slowest/fastest instance as an exemplar.</p>
          <div class="tile-error" id="err-spread"></div>
          <div class="chart-body" id="body-spread"></div>
        </div>

        <div class="card tile-duration" id="tile-duration">
          <h2>Duration over time</h2>
          <p class="caption">Median &middot; p95 &middot; min&ndash;max envelope of completed-instance duration, bucketed by completion time.</p>
          <div class="tile-controls">
            <div class="range-toggle" id="duration-range-toggle">
              <button type="button" data-range="15m" class="active">15m</button>
              <button type="button" data-range="1h">1h</button>
              <button type="button" data-range="6h">6h</button>
              <button type="button" data-range="24h">24h</button>
            </div>
            <select id="duration-process-select" aria-label="Filter duration-over-time by process">
              <option value="">All processes</option>
            </select>
          </div>
          <div class="tile-error" id="err-duration"></div>
          <div class="chart-body" id="body-duration"></div>
        </div>

      </div>

      <div class="chart-tooltip" id="tooltip"></div>

      <script>
        const REFRESH_MS = 10000;

        const SQL = {
          stats: `SELECT
                    (SELECT count(*) FROM instances) AS total_completed,
                    (SELECT count(*) FROM open_instances) AS running_now,
                    (SELECT count(*) FROM instances WHERE ended_at > now() - INTERVAL '300 seconds') AS completed_last_5m,
                    (SELECT quantile_cont(duration_ms, 0.95) FROM instances) AS p95_duration_ms`,
          line: `SELECT date_trunc('minute', ended_at) AS minute,
                        count(*) AS completions
                 FROM instances
                 WHERE ended_at > now() - INTERVAL '900 seconds'
                 GROUP BY 1
                 ORDER BY 1`,
          bars: `SELECT process_id, count(*) AS instance_count
                 FROM instances
                 GROUP BY process_id
                 ORDER BY instance_count DESC`,
          pctl: `SELECT process_id,
                        quantile_cont(duration_ms, 0.5) AS p50_duration_ms,
                        quantile_cont(duration_ms, 0.95) AS p95_duration_ms
                 FROM instances
                 GROUP BY process_id
                 ORDER BY p95_duration_ms DESC`,
          region: `SELECT coalesce(json_extract_string(vars_json, '$.region'), '(none)') AS region,
                          count(*) AS instance_count
                   FROM instances
                   GROUP BY 1
                   ORDER BY instance_count DESC`,
          spread: `SELECT process_id, count(*) AS n,
                          quantile_cont(duration_ms, 0.5) AS median_ms,
                          quantile_cont(duration_ms, 0.95) AS p95_ms,
                          min(duration_ms) AS min_ms, arg_min(key, duration_ms) AS min_key,
                          max(duration_ms) AS max_ms, arg_max(key, duration_ms) AS max_key
                   FROM instances
                   GROUP BY process_id
                   ORDER BY p95_ms DESC`,
        };

        // -------------------------------------------------------------------------------------------
        // Formatting helpers
        // -------------------------------------------------------------------------------------------

        function trimTrailingZero(s) {
          return s.endsWith('.0') ? s.slice(0, -2) : s;
        }

        function formatCount(n) {
          if (n === null || n === undefined || Number.isNaN(n)) {
            return '—';
          }
          const abs = Math.abs(n);
          if (abs >= 1e6) {
            return trimTrailingZero((n / 1e6).toFixed(1)) + 'M';
          }
          if (abs >= 1e4) {
            return trimTrailingZero((n / 1e3).toFixed(1)) + 'K';
          }
          return Math.round(n).toLocaleString('en-US');
        }

        function humanizeMs(ms) {
          if (ms === null || ms === undefined || Number.isNaN(ms)) {
            return '—';
          }
          const s = ms / 1000;
          if (s < 1) {
            return Math.round(ms) + ' ms';
          }
          if (s < 10) {
            return s.toFixed(1) + ' s';
          }
          if (s < 60) {
            return Math.round(s) + ' s';
          }
          const m = s / 60;
          if (m < 60) {
            return m.toFixed(1) + ' min';
          }
          const h = m / 60;
          return h.toFixed(1) + ' h';
        }

        function formatMinuteLabel(date) {
          return date.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
        }

        // -------------------------------------------------------------------------------------------
        // Tiny SVG helper
        // -------------------------------------------------------------------------------------------

        const SVG_NS = 'http://www.w3.org/2000/svg';

        function svgEl(tag, attrs) {
          const el = document.createElementNS(SVG_NS, tag);
          if (attrs) {
            for (const key of Object.keys(attrs)) {
              el.setAttribute(key, attrs[key]);
            }
          }
          return el;
        }

        // -------------------------------------------------------------------------------------------
        // Tooltip (single shared instance across all charts)
        // -------------------------------------------------------------------------------------------

        const tooltipEl = document.getElementById('tooltip');

        // `extraNodes` (optional): already-built DOM nodes appended after the plain label/value
        // rows -- tile 6 uses this for its two clickable exemplar-key rows (see makeExemplarRow),
        // everything else just omits the argument. Passing any extraNodes also marks the tooltip
        // 'interactive' (pointer-events re-enabled, see the .chart-tooltip.interactive CSS rule)
        // since that's the only case anything inside the tooltip needs to receive clicks at all.
        function showTooltip(clientX, clientY, titleText, rows, extraNodes) {
          tooltipEl.replaceChildren();
          const titleEl = document.createElement('div');
          titleEl.className = 'tt-title';
          titleEl.textContent = titleText;
          tooltipEl.appendChild(titleEl);
          for (const row of rows) {
            const rowEl = document.createElement('div');
            rowEl.className = 'tt-row';
            if (row.color) {
              const key = document.createElement('span');
              key.className = 'tt-key';
              key.style.background = row.color;
              rowEl.appendChild(key);
            }
            const label = document.createElement('span');
            label.textContent = row.label;
            rowEl.appendChild(label);
            const value = document.createElement('span');
            value.className = 'tt-value';
            value.textContent = row.value;
            rowEl.appendChild(value);
            tooltipEl.appendChild(rowEl);
          }
          if (extraNodes && extraNodes.length > 0) {
            for (const node of extraNodes) {
              tooltipEl.appendChild(node);
            }
            tooltipEl.classList.add('interactive');
          } else {
            tooltipEl.classList.remove('interactive');
          }
          tooltipEl.style.display = 'block';
          const x = Math.min(clientX + 14, window.innerWidth - 250);
          const y = Math.max(clientY - 12, 8);
          tooltipEl.style.left = x + 'px';
          tooltipEl.style.top = y + 'px';
        }

        function hideTooltip() {
          tooltipEl.style.display = 'none';
          tooltipEl.classList.remove('interactive');
        }

        // Grace-period hide: only used by tile 6's row interaction (see below). A plain hideTooltip()
        // on pointerleave would fire the instant the cursor left the row on its way to the tooltip's
        // own clickable <code> exemplar, closing it before a click could land. Giving the pointer a
        // short window to reach the (now pointer-events:auto) tooltip fixes that, without changing
        // behavior for every other tile's tooltip (they still call hideTooltip() directly).
        let tooltipHideTimer = null;

        function scheduleHideTooltip() {
          tooltipHideTimer = window.setTimeout(hideTooltip, 200);
        }

        tooltipEl.addEventListener('pointerenter', () => {
          if (tooltipHideTimer !== null) {
            clearTimeout(tooltipHideTimer);
            tooltipHideTimer = null;
          }
        });
        tooltipEl.addEventListener('pointerleave', scheduleHideTooltip);

        /**
         * Wires one hit-target rect for both pointer hover and keyboard focus, so every tooltip is
         * reachable without a mouse -- focus uses the hit rect's own screen position rather than a
         * cursor coordinate.
         */
        function attachRowInteraction(hitRect, onActivate, onDeactivate) {
          hitRect.setAttribute('tabindex', '0');
          hitRect.addEventListener('pointermove', (evt) => onActivate(evt.clientX, evt.clientY));
          hitRect.addEventListener('pointerleave', onDeactivate);
          hitRect.addEventListener('focus', () => {
            const box = hitRect.getBoundingClientRect();
            onActivate(box.left + box.width / 2, box.top);
          });
          hitRect.addEventListener('blur', onDeactivate);
        }

        // -------------------------------------------------------------------------------------------
        // Fetch + error/loading plumbing
        // -------------------------------------------------------------------------------------------

        async function loadTile(name, sql, onRows) {
          const card = document.getElementById('tile-' + name);
          const errEl = document.getElementById('err-' + name);
          const bodyEl = document.getElementById('body-' + name);
          card.classList.add('is-loading');
          try {
            const response = await fetch('/api/query', { method: 'POST', body: sql });
            const json = await response.json();
            if (json.error) {
              errEl.textContent = json.error;
              errEl.classList.add('visible');
              bodyEl.classList.add('hidden');
              return;
            }
            errEl.classList.remove('visible');
            bodyEl.classList.remove('hidden');
            onRows(json.columns, json.rows);
          } catch (e) {
            errEl.textContent = 'Request failed: ' + String(e);
            errEl.classList.add('visible');
            bodyEl.classList.add('hidden');
          } finally {
            card.classList.remove('is-loading');
          }
        }

        function rowsToObjects(columns, rows) {
          return rows.map((row) => {
            const obj = {};
            columns.forEach((col, i) => { obj[col] = row[i]; });
            return obj;
          });
        }

        function buildDataTable(columns, rows) {
          const details = document.createElement('details');
          details.className = 'data-table';
          const summary = document.createElement('summary');
          summary.textContent = 'Show data table';
          details.appendChild(summary);
          const table = document.createElement('table');
          const thead = document.createElement('thead');
          const headRow = document.createElement('tr');
          for (const col of columns) {
            const th = document.createElement('th');
            th.textContent = col;
            headRow.appendChild(th);
          }
          thead.appendChild(headRow);
          table.appendChild(thead);
          const tbody = document.createElement('tbody');
          for (const row of rows) {
            const tr = document.createElement('tr');
            for (const value of row) {
              const td = document.createElement('td');
              td.textContent = value === null ? '—' : String(value);
              tr.appendChild(td);
            }
            tbody.appendChild(tr);
          }
          table.appendChild(tbody);
          details.appendChild(table);
          return details;
        }

        // -------------------------------------------------------------------------------------------
        // Tile 1: stat row
        // -------------------------------------------------------------------------------------------

        function renderStats(columns, rows) {
          const row = rowsToObjects(columns, rows)[0] || {};
          document.getElementById('stat-total').textContent = formatCount(row.total_completed);
          document.getElementById('stat-running').textContent = formatCount(row.running_now);
          document.getElementById('stat-recent').textContent = formatCount(row.completed_last_5m);
          document.getElementById('stat-p95').textContent = humanizeMs(row.p95_duration_ms);
        }

        // -------------------------------------------------------------------------------------------
        // Tile 2: completions per minute -- single-hue line/area chart, crosshair + tooltip
        // -------------------------------------------------------------------------------------------

        function buildMinuteBuckets(columns, rows) {
          const objects = rowsToObjects(columns, rows);
          const byMinuteKey = new Map();
          for (const obj of objects) {
            const t = new Date(obj.minute);
            byMinuteKey.set(Math.floor(t.getTime() / 60000), Number(obj.completions) || 0);
          }
          const nowKey = Math.floor(Date.now() / 60000);
          const buckets = [];
          for (let i = 14; i >= 0; i--) {
            const key = nowKey - i;
            buckets.push({
              time: new Date(key * 60000),
              count: byMinuteKey.has(key) ? byMinuteKey.get(key) : 0,
            });
          }
          return buckets;
        }

        function renderLineChart(container, buckets) {
          container.replaceChildren();

          const width = 640;
          const height = 220;
          const marginLeft = 34;
          const marginRight = 44;
          const marginTop = 14;
          const marginBottom = 26;
          const plotWidth = width - marginLeft - marginRight;
          const plotHeight = height - marginTop - marginBottom;

          const maxCount = Math.max(1, ...buckets.map((b) => b.count));
          const yMax = maxCount * 1.15;
          const n = buckets.length;

          const xAt = (i) => marginLeft + (n === 1 ? 0 : (i / (n - 1)) * plotWidth);
          const yAt = (v) => marginTop + plotHeight - (v / yMax) * plotHeight;

          const svg = svgEl('svg', {
            class: 'chart-svg',
            viewBox: `0 0 ${width} ${height}`,
            role: 'img',
            'aria-label': 'Completions per minute over the last 15 minutes',
          });

          // Gridlines (recessive, hairline) at 3 nice steps.
          const gridSteps = 3;
          for (let s = 1; s <= gridSteps; s++) {
            const v = (yMax / gridSteps) * s;
            const y = yAt(v);
            svg.appendChild(svgEl('line', {
              class: 'grid-line', x1: marginLeft, x2: width - marginRight, y1: y, y2: y,
            }));
            const label = svgEl('text', { x: marginLeft - 8, y: y + 3, 'text-anchor': 'end' });
            label.textContent = String(Math.round(v));
            svg.appendChild(label);
          }

          // Baseline.
          const baseY = yAt(0);
          svg.appendChild(svgEl('line', {
            class: 'baseline', x1: marginLeft, x2: width - marginRight, y1: baseY, y2: baseY,
          }));

          // Area fill (10% wash) + 2px line, single hue.
          const points = buckets.map((b, i) => [xAt(i), yAt(b.count)]);
          let areaPath = `M ${points[0][0]} ${baseY}`;
          for (const [x, y] of points) {
            areaPath += ` L ${x} ${y}`;
          }
          areaPath += ` L ${points[points.length - 1][0]} ${baseY} Z`;
          svg.appendChild(svgEl('path', { d: areaPath, fill: 'var(--series-1-wash)', stroke: 'none' }));

          let linePath = `M ${points[0][0]} ${points[0][1]}`;
          for (const [x, y] of points.slice(1)) {
            linePath += ` L ${x} ${y}`;
          }
          svg.appendChild(svgEl('path', {
            d: linePath, fill: 'none', stroke: 'var(--series-1)', 'stroke-width': 2,
            'stroke-linejoin': 'round', 'stroke-linecap': 'round',
          }));

          // X-axis labels: start, middle, end only -- selective labeling, not one per point.
          [0, Math.floor((n - 1) / 2), n - 1].forEach((i) => {
            const label = svgEl('text', { x: xAt(i), y: height - 6, 'text-anchor': i === 0 ? 'start' : (i === n - 1 ? 'end' : 'middle') });
            label.textContent = formatMinuteLabel(buckets[i].time);
            svg.appendChild(label);
          });

          // Direct end label -- the value the line ends on.
          const lastPoint = points[points.length - 1];
          const endLabel = svgEl('text', {
            class: 'value-label', x: lastPoint[0] + 6, y: lastPoint[1] - 6, 'text-anchor': 'start',
          });
          endLabel.textContent = String(buckets[n - 1].count);
          svg.appendChild(endLabel);
          svg.appendChild(svgEl('circle', {
            cx: lastPoint[0], cy: lastPoint[1], r: 4, fill: 'var(--series-1)',
            stroke: 'var(--surface)', 'stroke-width': 2,
          }));

          // Crosshair (hidden until hover) -- snaps to nearest minute.
          const crosshair = svgEl('line', {
            class: 'baseline', x1: 0, x2: 0, y1: marginTop, y2: marginTop + plotHeight,
            stroke: 'var(--ink-muted)', 'stroke-width': 1, visibility: 'hidden',
          });
          const crosshairDot = svgEl('circle', {
            r: 4, fill: 'var(--series-1)', stroke: 'var(--surface)', 'stroke-width': 2, visibility: 'hidden',
          });
          svg.appendChild(crosshair);
          svg.appendChild(crosshairDot);

          // One large hit rect over the whole plot -- hit target far bigger than the 2px line.
          const hitRect = svgEl('rect', {
            class: 'hit-rect', x: marginLeft, y: 0, width: plotWidth, height: height,
          });
          attachRowInteraction(hitRect, (clientX, clientY) => {
            const rect = svg.getBoundingClientRect();
            const scaleX = width / rect.width;
            const localX = (clientX - rect.left) * scaleX;
            let nearest = 0;
            let nearestDist = Infinity;
            points.forEach(([x], i) => {
              const dist = Math.abs(x - localX);
              if (dist < nearestDist) {
                nearestDist = dist;
                nearest = i;
              }
            });
            const [px, py] = points[nearest];
            crosshair.setAttribute('x1', px);
            crosshair.setAttribute('x2', px);
            crosshair.setAttribute('visibility', 'visible');
            crosshairDot.setAttribute('cx', px);
            crosshairDot.setAttribute('cy', py);
            crosshairDot.setAttribute('visibility', 'visible');
            showTooltip(clientX, clientY, formatMinuteLabel(buckets[nearest].time), [
              { color: 'var(--series-1)', label: 'Completions', value: String(buckets[nearest].count) },
            ]);
          }, () => {
            crosshair.setAttribute('visibility', 'hidden');
            crosshairDot.setAttribute('visibility', 'hidden');
            hideTooltip();
          });
          svg.appendChild(hitRect);

          container.appendChild(svg);
          container.appendChild(buildDataTable(['minute', 'completions'],
            buckets.map((b) => [formatMinuteLabel(b.time), b.count])));
        }

        // -------------------------------------------------------------------------------------------
        // Tiles 3 & 5: single-series horizontal bars with direct end labels
        // -------------------------------------------------------------------------------------------

        function renderHBarChart(container, rows, opts) {
          container.replaceChildren();

          const barHeight = 20;
          const rowGap = 10;
          const labelWidth = 150;
          const width = 560;
          const marginRight = 56;
          const marginTop = 6;
          const plotWidth = width - labelWidth - marginRight;
          const rowHeight = barHeight + rowGap;
          const height = marginTop + rows.length * rowHeight;

          const maxValue = Math.max(1, ...rows.map((r) => opts.value(r)));

          const svg = svgEl('svg', {
            class: 'chart-svg', viewBox: `0 0 ${width} ${height}`, role: 'img',
            'aria-label': opts.ariaLabel,
          });

          rows.forEach((r, i) => {
            const value = opts.value(r);
            const barW = Math.max(2, (value / maxValue) * plotWidth);
            const y = marginTop + i * rowHeight;

            // Hit target spans the full row width (label column too) and full row height + gap.
            const hitRect = svgEl('rect', {
              class: 'hit-rect', x: 0, y: y - rowGap / 2, width: width, height: rowHeight,
            });

            const label = svgEl('text', {
              class: 'category-label', x: labelWidth - 10, y: y + barHeight / 2 + 4, 'text-anchor': 'end',
            });
            label.textContent = opts.label(r);

            const bar = svgEl('rect', {
              class: 'bar-mark', x: labelWidth, y, width: barW, height: barHeight,
              rx: 4, ry: 4, fill: 'var(--series-1)',
            });

            const valueLabel = svgEl('text', {
              class: 'value-label', x: labelWidth + barW + 8, y: y + barHeight / 2 + 4, 'text-anchor': 'start',
            });
            // formatValue's second (row) argument is optional -- callers that only need the value
            // itself can ignore it.
            valueLabel.textContent = opts.formatValue(value, r);

            attachRowInteraction(hitRect, (clientX, clientY) => {
              bar.classList.add('hovered');
              showTooltip(clientX, clientY, opts.label(r), [
                { color: 'var(--series-1)', label: opts.valueLabel, value: opts.formatValue(value, r) },
              ]);
            }, () => {
              bar.classList.remove('hovered');
              hideTooltip();
            });

            svg.appendChild(label);
            svg.appendChild(bar);
            svg.appendChild(valueLabel);
            svg.appendChild(hitRect);
          });

          container.appendChild(svg);
          container.appendChild(buildDataTable(
            [opts.labelColumn, opts.valueColumn],
            rows.map((r) => [opts.label(r), opts.formatValue(opts.value(r), r)]),
          ));
        }

      """);

  /**
   * See {@link #DASHBOARD_HTML_PART1} -- second half of the same page, split purely because {@code
   * javac} rejects a single string-literal constant (including a text block) past 65535 bytes of
   * modified-UTF-8; there is nothing semantically separate about the two halves. Concatenated back
   * into {@link #DASHBOARD_HTML}; both halves are routed through {@link #asIs(String)} so that
   * concatenation, too, is never itself folded back into one over-long compile-time constant.
   */
  private static final String DASHBOARD_HTML_PART2 =
      asIs(
          """
        // -------------------------------------------------------------------------------------------
        // Tile 4: grouped horizontal bars -- two series (p50 / p95), legend, fixed categorical order
        // -------------------------------------------------------------------------------------------

        function renderGroupedHBarChart(container, rows) {
          container.replaceChildren();

          const barHeight = 14;
          const barGap = 2;
          const rowGap = 12;
          const labelWidth = 150;
          const width = 560;
          const marginRight = 60;
          const marginTop = 6;
          const plotWidth = width - labelWidth - marginRight;
          const groupHeight = barHeight * 2 + barGap;
          const rowHeight = groupHeight + rowGap;
          const height = marginTop + rows.length * rowHeight;

          const maxValue = Math.max(1, ...rows.map((r) => Math.max(r.p50_duration_ms || 0, r.p95_duration_ms || 0)));

          const legend = document.createElement('div');
          legend.className = 'legend';
          legend.innerHTML =
            '<span class="legend-item"><span class="legend-key" style="background:var(--series-1)"></span>p50</span>' +
            '<span class="legend-item"><span class="legend-key" style="background:var(--series-2)"></span>p95</span>';
          container.appendChild(legend);

          const svg = svgEl('svg', {
            class: 'chart-svg', viewBox: `0 0 ${width} ${height}`, role: 'img',
            'aria-label': 'p50 and p95 duration per process',
          });

          rows.forEach((r, i) => {
            const y0 = marginTop + i * rowHeight;

            const hitRect = svgEl('rect', {
              class: 'hit-rect', x: 0, y: y0 - rowGap / 2, width: width, height: rowHeight,
            });

            const label = svgEl('text', {
              class: 'category-label', x: labelWidth - 10, y: y0 + groupHeight / 2 + 4, 'text-anchor': 'end',
            });
            label.textContent = r.process_id;

            const series = [
              { key: 'p50_duration_ms', name: 'p50', color: 'var(--series-1)', y: y0 },
              { key: 'p95_duration_ms', name: 'p95', color: 'var(--series-2)', y: y0 + barHeight + barGap },
            ];

            const barEls = [];
            for (const s of series) {
              const value = r[s.key] || 0;
              const barW = Math.max(2, (value / maxValue) * plotWidth);
              const bar = svgEl('rect', {
                class: 'bar-mark', x: labelWidth, y: s.y, width: barW, height: barHeight,
                rx: 4, ry: 4, fill: s.color,
              });
              const valueLabel = svgEl('text', {
                class: 'value-label', x: labelWidth + barW + 8, y: s.y + barHeight / 2 + 4,
                'text-anchor': 'start',
              });
              valueLabel.textContent = humanizeMs(value);
              svg.appendChild(bar);
              svg.appendChild(valueLabel);
              barEls.push(bar);
            }

            attachRowInteraction(hitRect, (clientX, clientY) => {
              barEls.forEach((b) => b.classList.add('hovered'));
              showTooltip(clientX, clientY, r.process_id, series.map((s) => ({
                color: s.color, label: s.name, value: humanizeMs(r[s.key] || 0),
              })));
            }, () => {
              barEls.forEach((b) => b.classList.remove('hovered'));
              hideTooltip();
            });

            svg.appendChild(label);
            svg.appendChild(hitRect);
          });

          container.appendChild(svg);
          container.appendChild(buildDataTable(
            ['process_id', 'p50', 'p95'],
            rows.map((r) => [r.process_id, humanizeMs(r.p50_duration_ms), humanizeMs(r.p95_duration_ms)]),
          ));
        }

        // -------------------------------------------------------------------------------------------
        // Tile 6: duration spread per process -- horizontal min..max range plot with median/p95
        // markers (shape, not color, carries their identity -- all marks share --series-1, the
        // page's one categorical hue) and clickable exemplar instance keys in the tooltip.
        // -------------------------------------------------------------------------------------------

        /** One shape-legend entry, drawn as a small inline SVG rather than a colored swatch. */
        function buildLegendShape(kind) {
          const svg = svgEl('svg', { width: 14, height: 14, viewBox: '0 0 14 14' });
          if (kind === 'median') {
            svg.appendChild(svgEl('circle', { cx: 7, cy: 7, r: 5, fill: 'var(--series-1)' }));
          } else if (kind === 'p95') {
            svg.appendChild(svgEl('rect', {
              x: 3, y: 3, width: 8, height: 8, fill: 'var(--series-1)', transform: 'rotate(45 7 7)',
            }));
          } else {
            // min and max share the same open-circle mark -- position (left end vs. right end of
            // the row's range line), not shape, is what tells them apart in the chart itself.
            svg.appendChild(svgEl('circle', {
              cx: 7, cy: 7, r: 4, fill: 'none', stroke: 'var(--series-1)', 'stroke-width': 1.5,
            }));
          }
          return svg;
        }

        function renderSpreadLegend(container) {
          const legend = document.createElement('div');
          legend.className = 'legend';
          for (const item of [
            ['median', 'median'], ['p95', 'p95'], ['min', 'min'], ['max', 'max'],
          ]) {
            const wrap = document.createElement('span');
            wrap.className = 'legend-item';
            wrap.appendChild(buildLegendShape(item[0]));
            const text = document.createElement('span');
            text.textContent = item[1];
            wrap.appendChild(text);
            legend.appendChild(wrap);
          }
          container.appendChild(legend);
        }

        /**
         * One tooltip row for a clickable exemplar instance key -- click copies the key to the
         * clipboard and flashes "copied!" in its place for a moment. A fresh element (and listener)
         * is built on every hover, so there's nothing to clean up between hovers.
         */
        function makeExemplarRow(label, keyValue) {
          const row = document.createElement('div');
          row.className = 'tt-row tt-exemplar';
          const labelEl = document.createElement('span');
          labelEl.textContent = label + ':';
          row.appendChild(labelEl);
          const code = document.createElement('code');
          code.className = 'tt-key-code';
          code.textContent = String(keyValue);
          code.title = 'Click to copy';
          code.addEventListener('click', (evt) => {
            evt.stopPropagation();
            navigator.clipboard.writeText(String(keyValue)).then(() => {
              code.textContent = 'copied!';
              code.classList.add('copied');
              window.setTimeout(() => {
                code.textContent = String(keyValue);
                code.classList.remove('copied');
              }, 900);
            });
          });
          row.appendChild(code);
          return row;
        }

        /**
         * Dynamic log/linear duration-axis scale, shared by tile 6 (duration spread per process)
         * and tile 7 (duration over time) so the two tiles agree on when to reach for a log axis.
         * Durations across this demo's processes are heavy-tailed: per-row min/max ratios from ~6x
         * up to ~500x, and the overall cross-process/cross-time range is wider still (validated live
         * against /api/query -- see the module README's demo-query section for the exact numbers). A
         * linear axis would crush every fast completion into an unreadable sliver against the
         * slowest one, so switch to log10 once the domain's max/min ratio passes ~50x; below that
         * threshold linear reads better (a small ratio doesn't need log, and log scales are harder to
         * eyeball for near-uniform data).
         */
        function chooseDurationScale(domainMin, domainMax) {
          const useLogScale = domainMin > 0 && domainMax / domainMin > 50;
          const toLog = (v) => Math.log10(Math.max(v, domainMin));
          const logMin = toLog(domainMin);
          const logMax = toLog(domainMax);
          const logSpan = logMax - logMin || 1;
          const linSpan = (domainMax - domainMin) || 1;
          return {
            useLogScale,
            // Raw duration value -> 0..1 fraction of the domain, in whichever space was picked above.
            toFrac: (v) => (useLogScale ? (toLog(v) - logMin) / logSpan : (v - domainMin) / linSpan),
            // Inverse of toFrac -- used to place evenly-spaced axis ticks.
            fracToValue: (frac) => (useLogScale ? Math.pow(10, logMin + frac * logSpan) : domainMin + frac * linSpan),
          };
        }

        function renderRangeSpreadChart(container, rows) {
          container.replaceChildren();
          renderSpreadLegend(container);

          const rowHeight = 34;
          const labelWidth = 150;
          const width = 560;
          const marginRight = 64;
          const marginTop = 6;
          const marginBottom = 20;
          const plotWidth = width - labelWidth - marginRight;
          const height = marginTop + rows.length * rowHeight + marginBottom;

          const domainMin = Math.min(...rows.map((r) => Number(r.min_ms)));
          const domainMax = Math.max(...rows.map((r) => Number(r.max_ms)));
          const scale = chooseDurationScale(domainMin, domainMax);
          const useLogScale = scale.useLogScale;
          const xScale = (v) => labelWidth + scale.toFrac(v) * plotWidth;

          const svg = svgEl('svg', {
            class: 'chart-svg', viewBox: `0 0 ${width} ${height}`, role: 'img',
            'aria-label': 'Duration spread -- min, median, p95 and max -- per process',
          });

          // X-axis: a handful of ticks evenly spaced in whichever space (log or linear) the scale
          // above picked, humanized like every other duration on this page.
          const tickCount = 4;
          for (let t = 0; t <= tickCount; t++) {
            const frac = t / tickCount;
            const value = scale.fracToValue(frac);
            const x = xScale(value);
            svg.appendChild(svgEl('line', {
              class: 'grid-line', x1: x, x2: x, y1: marginTop, y2: height - marginBottom,
            }));
            const tick = svgEl('text', {
              x, y: height - marginBottom + 14,
              'text-anchor': t === 0 ? 'start' : (t === tickCount ? 'end' : 'middle'),
            });
            tick.textContent = humanizeMs(value);
            svg.appendChild(tick);
          }

          rows.forEach((r, i) => {
            const y = marginTop + i * rowHeight + rowHeight / 2;
            const minMs = Number(r.min_ms);
            const maxMs = Number(r.max_ms);
            const medianMs = Number(r.median_ms);
            const p95Ms = Number(r.p95_ms);
            const minX = xScale(minMs);
            const maxX = xScale(maxMs);
            const medianX = xScale(medianMs);
            const p95X = xScale(p95Ms);

            const hitRect = svgEl('rect', {
              class: 'hit-rect', x: 0, y: y - rowHeight / 2, width: width, height: rowHeight,
            });

            const label = svgEl('text', {
              class: 'category-label', x: labelWidth - 10, y: y + 4, 'text-anchor': 'end',
            });
            label.textContent = r.process_id;

            // The range line is context, not a series -- muted gridline ink, not the categorical hue.
            const rangeLine = svgEl('line', {
              class: 'bar-mark', x1: minX, x2: maxX, y1: y, y2: y,
              stroke: 'var(--gridline)', 'stroke-width': 2, 'stroke-linecap': 'round',
            });
            const minMarker = svgEl('circle', {
              class: 'bar-mark', cx: minX, cy: y, r: 3, fill: 'none',
              stroke: 'var(--series-1)', 'stroke-width': 1.5,
            });
            const maxMarker = svgEl('circle', {
              class: 'bar-mark', cx: maxX, cy: y, r: 3, fill: 'none',
              stroke: 'var(--series-1)', 'stroke-width': 1.5,
            });
            const medianMarker = svgEl('circle', {
              class: 'bar-mark', cx: medianX, cy: y, r: 5, fill: 'var(--series-1)',
            });
            const p95Marker = svgEl('rect', {
              class: 'bar-mark', x: p95X - 4, y: y - 4, width: 8, height: 8, fill: 'var(--series-1)',
              transform: `rotate(45 ${p95X} ${y})`,
            });

            // Direct label: only the max marker gets one -- min/median/p95 live in the tooltip.
            const maxLabel = svgEl('text', {
              class: 'value-label', x: maxX + 8, y: y + 4, 'text-anchor': 'start',
            });
            maxLabel.textContent = humanizeMs(maxMs);

            const marks = [rangeLine, minMarker, maxMarker, medianMarker, p95Marker];

            attachRowInteraction(hitRect, (clientX, clientY) => {
              marks.forEach((m) => m.classList.add('hovered'));
              showTooltip(clientX, clientY, r.process_id, [
                { label: 'n', value: formatCount(Number(r.n)) },
                { label: 'median', value: humanizeMs(medianMs) },
                { label: 'p95', value: humanizeMs(p95Ms) },
                { label: 'min', value: humanizeMs(minMs) },
                { label: 'max', value: humanizeMs(maxMs) },
              ], [
                makeExemplarRow('slowest', r.max_key),
                makeExemplarRow('fastest', r.min_key),
              ]);
            }, () => {
              marks.forEach((m) => m.classList.remove('hovered'));
              scheduleHideTooltip();
            });

            svg.appendChild(label);
            svg.appendChild(rangeLine);
            svg.appendChild(minMarker);
            svg.appendChild(maxMarker);
            svg.appendChild(medianMarker);
            svg.appendChild(p95Marker);
            svg.appendChild(maxLabel);
            svg.appendChild(hitRect);
          });

          container.appendChild(svg);

          const note = document.createElement('p');
          note.className = 'caption';
          note.textContent =
            'min/max carry their instance keys — hover a row, click a key to copy the exemplar.';
          container.appendChild(note);

          container.appendChild(buildDataTable(
            ['process_id', 'n', 'median_ms', 'p95_ms', 'min_ms', 'min_key', 'max_ms', 'max_key'],
            rows.map((r) => [
              r.process_id, formatCount(Number(r.n)), humanizeMs(Number(r.median_ms)),
              humanizeMs(Number(r.p95_ms)), humanizeMs(Number(r.min_ms)), r.min_key,
              humanizeMs(Number(r.max_ms)), r.max_key,
            ]),
          ));
        }

        // -------------------------------------------------------------------------------------------
        // Tile 7: duration over time -- min/max envelope + median/p95 lines, bucketed by completion
        // time, with a time-range toggle and a process filter. Reuses tile 6's crosshair/tooltip
        // machinery (attachRowInteraction, makeExemplarRow, scheduleHideTooltip) and its dynamic
        // log/linear scale (chooseDurationScale, defined just above tile 6's own render function).
        // -------------------------------------------------------------------------------------------

        // Four fixed (window, bucket) pairs -- the bucket width is chosen so each window always
        // renders a readable, fixed number of points (15/30/36/48), not so many that the chart turns
        // into noise, not so few that the shape of the trend is lost.
        const DURATION_RANGES = [
          { key: '15m', label: '15m', seconds: 15 * 60, bucketSql: '1 minute', bucketMs: 60 * 1000 },
          { key: '1h', label: '1h', seconds: 60 * 60, bucketSql: '2 minute', bucketMs: 2 * 60 * 1000 },
          { key: '6h', label: '6h', seconds: 6 * 60 * 60, bucketSql: '10 minute', bucketMs: 10 * 60 * 1000 },
          { key: '24h', label: '24h', seconds: 24 * 60 * 60, bucketSql: '30 minute', bucketMs: 30 * 60 * 1000 },
        ];

        // Plain JS state -- persists across the 10s auto-refresh and across manual re-fetches alike,
        // exactly like every other piece of client-side UI state on this page (there's no server
        // session to persist it in, nor any need for one at this demo's scale).
        let durationRangeKey = '15m';
        let durationProcessFilter = ''; // '' = all processes

        function buildDurationSql(range, processFilter) {
          const filterClause = processFilter
            ? ` AND process_id = '${processFilter.replace(/'/g, "''")}'`
            : '';
          return `SELECT time_bucket(INTERVAL '${range.bucketSql}', ended_at) AS bucket,
                         count(*) AS n,
                         quantile_cont(duration_ms, 0.5) AS median_ms,
                         quantile_cont(duration_ms, 0.95) AS p95_ms,
                         min(duration_ms) AS min_ms, arg_min(key, duration_ms) AS min_key,
                         max(duration_ms) AS max_ms, arg_max(key, duration_ms) AS max_key
                  FROM instances
                  WHERE ended_at > now() - INTERVAL '${range.seconds} seconds'${filterClause}
                  GROUP BY bucket
                  ORDER BY bucket`;
        }

        /**
         * Fills in every bucket across the selected window (like {@link buildMinuteBuckets} for
         * tile 2), but -- unlike tile 2 -- a bucket the query didn't return is NOT zero-filled here.
         * Zero completions means percentiles/min/max of an empty set, which simply don't exist;
         * treating that as "duration 0ms" would draw a data point that never happened. Those buckets
         * carry {@code n: 0} and every duration field {@code null}, and the renderer below turns runs
         * of {@code null} into gaps in the band and lines, not zeros.
         */
        function buildDurationBuckets(columns, rows, range) {
          const objects = rowsToObjects(columns, rows);
          const byBucketKey = new Map();
          for (const obj of objects) {
            const t = new Date(obj.bucket);
            byBucketKey.set(Math.floor(t.getTime() / range.bucketMs), obj);
          }
          const nowKey = Math.floor(Date.now() / range.bucketMs);
          const bucketCount = Math.round((range.seconds * 1000) / range.bucketMs);
          const buckets = [];
          for (let i = bucketCount - 1; i >= 0; i--) {
            const key = nowKey - i;
            const obj = byBucketKey.get(key);
            buckets.push({
              time: new Date(key * range.bucketMs),
              n: obj ? Number(obj.n) : 0,
              medianMs: obj ? Number(obj.median_ms) : null,
              p95Ms: obj ? Number(obj.p95_ms) : null,
              minMs: obj ? Number(obj.min_ms) : null,
              minKey: obj ? obj.min_key : null,
              maxMs: obj ? Number(obj.max_ms) : null,
              maxKey: obj ? obj.max_key : null,
            });
          }
          return buckets;
        }

        function formatBucketLabel(date) {
          return date.toLocaleString('en-GB', {
            day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
          });
        }

        /** Splits `buckets` into runs of consecutive indices for which `isValid` holds. */
        function contiguousSegments(buckets, isValid) {
          const segments = [];
          let current = [];
          buckets.forEach((b, i) => {
            if (isValid(b)) {
              current.push(i);
            } else if (current.length > 0) {
              segments.push(current);
              current = [];
            }
          });
          if (current.length > 0) {
            segments.push(current);
          }
          return segments;
        }

        /**
         * Draws one series (median or p95) as a set of line segments -- one path per contiguous run
         * of non-gap buckets, so a gap breaks the line instead of being bridged across it. A
         * single-point run (no neighbor to draw a line to) still gets a visible dot. Returns the
         * segments so the caller can find "the last plotted point" for the direct end label.
         */
        function buildLineSegments(svg, buckets, xAt, yAt, valueKey, colorVar) {
          const segments = contiguousSegments(buckets, (b) => b[valueKey] !== null);
          for (const seg of segments) {
            if (seg.length === 1) {
              const i = seg[0];
              svg.appendChild(svgEl('circle', {
                cx: xAt(i), cy: yAt(buckets[i][valueKey]), r: 3, fill: colorVar,
              }));
              continue;
            }
            let path = `M ${xAt(seg[0])} ${yAt(buckets[seg[0]][valueKey])}`;
            for (const i of seg.slice(1)) {
              path += ` L ${xAt(i)} ${yAt(buckets[i][valueKey])}`;
            }
            svg.appendChild(svgEl('path', {
              d: path, fill: 'none', stroke: colorVar, 'stroke-width': 2,
              'stroke-linejoin': 'round', 'stroke-linecap': 'round',
            }));
          }
          return segments;
        }

        function renderDurationChart(container, buckets, range) {
          container.replaceChildren();

          const legend = document.createElement('div');
          legend.className = 'legend';
          legend.innerHTML =
            '<span class="legend-item"><span class="legend-key" style="background:var(--series-1)"></span>median</span>' +
            '<span class="legend-item"><span class="legend-key" style="background:var(--series-2)"></span>p95</span>' +
            '<span class="legend-item"><span class="legend-key" style="background:var(--series-1-wash);width:14px;height:8px;border-radius:2px;"></span>min–max range</span>';
          container.appendChild(legend);

          const validIdx = buckets
            .map((b, i) => i)
            .filter((i) => buckets[i].n > 0 && buckets[i].minMs !== null);

          if (validIdx.length === 0) {
            const msg = document.createElement('p');
            msg.className = 'caption';
            msg.textContent = 'No completions in this window.';
            container.appendChild(msg);
            return;
          }

          const width = 1200;
          const height = 260;
          const marginLeft = 52;
          const marginRight = 72;
          const marginTop = 14;
          const marginBottom = 26;
          const plotWidth = width - marginLeft - marginRight;
          const plotHeight = height - marginTop - marginBottom;
          const n = buckets.length;

          const xAt = (i) => marginLeft + (n === 1 ? 0 : (i / (n - 1)) * plotWidth);

          const domainMin = Math.min(...validIdx.map((i) => buckets[i].minMs));
          const domainMax = Math.max(...validIdx.map((i) => buckets[i].maxMs));
          const scale = chooseDurationScale(domainMin, domainMax);
          const yAt = (v) => marginTop + plotHeight - scale.toFrac(v) * plotHeight;

          const svg = svgEl('svg', {
            class: 'chart-svg', viewBox: `0 0 ${width} ${height}`, role: 'img',
            'aria-label': `Duration percentiles over time, last ${range.label}`,
          });

          // Y-axis: a handful of ticks evenly spaced in whichever space (log or linear) the scale
          // above picked, humanized like every other duration on this page.
          const tickCount = 4;
          for (let t = 0; t <= tickCount; t++) {
            const value = scale.fracToValue(t / tickCount);
            const y = yAt(value);
            svg.appendChild(svgEl('line', {
              class: 'grid-line', x1: marginLeft, x2: width - marginRight, y1: y, y2: y,
            }));
            const label = svgEl('text', { x: marginLeft - 8, y: y + 3, 'text-anchor': 'end' });
            label.textContent = humanizeMs(value);
            svg.appendChild(label);
          }

          // Min-max envelope -- a muted band (series-1 wash), context rather than a series in its
          // own right, drawn per contiguous run so a gap breaks the band too.
          const bandSegments = contiguousSegments(buckets, (b) => b.n > 0 && b.minMs !== null);
          for (const seg of bandSegments) {
            if (seg.length < 2) {
              continue;
            }
            let path = `M ${xAt(seg[0])} ${yAt(buckets[seg[0]].minMs)}`;
            for (const i of seg.slice(1)) {
              path += ` L ${xAt(i)} ${yAt(buckets[i].minMs)}`;
            }
            for (let k = seg.length - 1; k >= 0; k--) {
              const i = seg[k];
              path += ` L ${xAt(i)} ${yAt(buckets[i].maxMs)}`;
            }
            path += ' Z';
            svg.appendChild(svgEl('path', { d: path, fill: 'var(--series-1-wash)', stroke: 'none' }));
          }

          buildLineSegments(svg, buckets, xAt, yAt, 'medianMs', 'var(--series-1)');
          const p95Segments = buildLineSegments(svg, buckets, xAt, yAt, 'p95Ms', 'var(--series-2)');

          // Direct label: the last actually-plotted p95 point (the true end of its line -- a gap at
          // the very end of the window must not pull the label back onto a fabricated position).
          if (p95Segments.length > 0) {
            const lastSeg = p95Segments[p95Segments.length - 1];
            const i = lastSeg[lastSeg.length - 1];
            const x = xAt(i);
            const y = yAt(buckets[i].p95Ms);
            const endLabel = svgEl('text', {
              class: 'value-label', x: x + 6, y: y - 6, 'text-anchor': 'start',
            });
            endLabel.textContent = humanizeMs(buckets[i].p95Ms);
            svg.appendChild(endLabel);
            svg.appendChild(svgEl('circle', {
              cx: x, cy: y, r: 4, fill: 'var(--series-2)', stroke: 'var(--surface)', 'stroke-width': 2,
            }));
          }

          // X-axis labels: start, middle, end only -- selective labeling, not one per point.
          [0, Math.floor((n - 1) / 2), n - 1].forEach((i) => {
            const label = svgEl('text', {
              x: xAt(i), y: height - 6,
              'text-anchor': i === 0 ? 'start' : (i === n - 1 ? 'end' : 'middle'),
            });
            label.textContent = formatMinuteLabel(buckets[i].time);
            svg.appendChild(label);
          });

          // Crosshair -- one shared vertical line, one dot per series (either dot hides on its own
          // if the nearest bucket happens to be a gap for that series).
          const crosshair = svgEl('line', {
            x1: 0, x2: 0, y1: marginTop, y2: marginTop + plotHeight,
            stroke: 'var(--ink-muted)', 'stroke-width': 1, visibility: 'hidden',
          });
          const medianDot = svgEl('circle', {
            r: 4, fill: 'var(--series-1)', stroke: 'var(--surface)', 'stroke-width': 2, visibility: 'hidden',
          });
          const p95Dot = svgEl('circle', {
            r: 4, fill: 'var(--series-2)', stroke: 'var(--surface)', 'stroke-width': 2, visibility: 'hidden',
          });
          svg.appendChild(crosshair);
          svg.appendChild(medianDot);
          svg.appendChild(p95Dot);

          const hitRect = svgEl('rect', {
            class: 'hit-rect', x: marginLeft, y: 0, width: plotWidth, height: height,
          });
          attachRowInteraction(hitRect, (clientX, clientY) => {
            const rect = svg.getBoundingClientRect();
            const scaleX = width / rect.width;
            const localX = (clientX - rect.left) * scaleX;
            let nearest = 0;
            let nearestDist = Infinity;
            buckets.forEach((b, i) => {
              const dist = Math.abs(xAt(i) - localX);
              if (dist < nearestDist) {
                nearestDist = dist;
                nearest = i;
              }
            });
            const b = buckets[nearest];
            const px = xAt(nearest);
            crosshair.setAttribute('x1', px);
            crosshair.setAttribute('x2', px);
            crosshair.setAttribute('visibility', 'visible');
            if (b.medianMs !== null) {
              medianDot.setAttribute('cx', px);
              medianDot.setAttribute('cy', yAt(b.medianMs));
              medianDot.setAttribute('visibility', 'visible');
            } else {
              medianDot.setAttribute('visibility', 'hidden');
            }
            if (b.p95Ms !== null) {
              p95Dot.setAttribute('cx', px);
              p95Dot.setAttribute('cy', yAt(b.p95Ms));
              p95Dot.setAttribute('visibility', 'visible');
            } else {
              p95Dot.setAttribute('visibility', 'hidden');
            }

            if (b.n === 0) {
              showTooltip(clientX, clientY, formatBucketLabel(b.time), [
                { label: 'completions', value: '0 (no data)' },
              ]);
              return;
            }
            showTooltip(clientX, clientY, formatBucketLabel(b.time), [
              { label: 'n', value: formatCount(b.n) },
              { color: 'var(--series-1)', label: 'median', value: humanizeMs(b.medianMs) },
              { color: 'var(--series-2)', label: 'p95', value: humanizeMs(b.p95Ms) },
              { label: 'min', value: humanizeMs(b.minMs) },
              { label: 'max', value: humanizeMs(b.maxMs) },
            ], [
              makeExemplarRow('slowest', b.maxKey),
              makeExemplarRow('fastest', b.minKey),
            ]);
          }, () => {
            crosshair.setAttribute('visibility', 'hidden');
            medianDot.setAttribute('visibility', 'hidden');
            p95Dot.setAttribute('visibility', 'hidden');
            // Grace-period hide, like tile 6: the tooltip's exemplar rows are click-to-copy, so a
            // pointer crossing from the hit rect into the (pointer-events:auto) tooltip must not
            // have the tooltip vanish out from under it first.
            scheduleHideTooltip();
          });
          svg.appendChild(hitRect);

          container.appendChild(svg);
          container.appendChild(buildDataTable(
            ['time', 'n', 'median_ms', 'p95_ms', 'min_ms', 'min_key', 'max_ms', 'max_key'],
            buckets.map((b) => [
              formatBucketLabel(b.time), formatCount(b.n), humanizeMs(b.medianMs), humanizeMs(b.p95Ms),
              humanizeMs(b.minMs), b.minKey, humanizeMs(b.maxMs), b.maxKey,
            ]),
          ));
        }

        function refreshDurationTile() {
          const range = DURATION_RANGES.find((r) => r.key === durationRangeKey);
          loadTile('duration', buildDurationSql(range, durationProcessFilter), (columns, rows) => {
            renderDurationChart(
              document.getElementById('body-duration'), buildDurationBuckets(columns, rows, range), range);
          });
        }

        /**
         * Refreshes the process <select>'s options from the live data (one tiny query per refresh
         * cycle), preserving the current selection if it's still among the options -- falling back
         * to "All processes" only if the previously-selected process has disappeared entirely.
         */
        async function refreshDurationProcessOptions() {
          try {
            const response = await fetch('/api/query', {
              method: 'POST', body: 'SELECT DISTINCT process_id FROM instances ORDER BY 1',
            });
            const json = await response.json();
            if (json.error) {
              return;
            }
            const select = document.getElementById('duration-process-select');
            const current = select.value;
            const processIds = rowsToObjects(json.columns, json.rows).map((r) => r.process_id);
            select.replaceChildren();
            const allOption = document.createElement('option');
            allOption.value = '';
            allOption.textContent = 'All processes';
            select.appendChild(allOption);
            for (const processId of processIds) {
              const option = document.createElement('option');
              option.value = processId;
              option.textContent = processId;
              select.appendChild(option);
            }
            select.value = processIds.includes(current) ? current : '';
            durationProcessFilter = select.value;
          } catch (e) {
            // Best-effort only -- leave whatever options are already there.
          }
        }

        document.querySelectorAll('#duration-range-toggle button').forEach((button) => {
          button.addEventListener('click', () => {
            if (button.dataset.range === durationRangeKey) {
              return;
            }
            durationRangeKey = button.dataset.range;
            document.querySelectorAll('#duration-range-toggle button').forEach((b) => {
              b.classList.toggle('active', b === button);
            });
            refreshDurationTile();
          });
        });
        document.getElementById('duration-process-select').addEventListener('change', (evt) => {
          durationProcessFilter = evt.target.value;
          refreshDurationTile();
        });

        // -------------------------------------------------------------------------------------------
        // Refresh loop -- auto-refresh every 10s, paused while the tab is hidden.
        // -------------------------------------------------------------------------------------------

        function refreshAll() {
          loadTile('stats', SQL.stats, renderStats);
          loadTile('line', SQL.line, (columns, rows) => {
            renderLineChart(document.getElementById('body-line'), buildMinuteBuckets(columns, rows));
          });
          loadTile('bars', SQL.bars, (columns, rows) => {
            renderHBarChart(document.getElementById('body-bars'), rowsToObjects(columns, rows), {
              label: (r) => r.process_id,
              value: (r) => Number(r.instance_count),
              formatValue: (v) => formatCount(v),
              valueLabel: 'Instances',
              labelColumn: 'process_id',
              valueColumn: 'instance_count',
              ariaLabel: 'Instances per process',
            });
          });
          loadTile('pctl', SQL.pctl, (columns, rows) => {
            renderGroupedHBarChart(document.getElementById('body-pctl'), rowsToObjects(columns, rows));
          });
          loadTile('region', SQL.region, (columns, rows) => {
            renderHBarChart(document.getElementById('body-region'), rowsToObjects(columns, rows), {
              label: (r) => r.region,
              value: (r) => Number(r.instance_count),
              formatValue: (v) => formatCount(v),
              valueLabel: 'Instances',
              labelColumn: 'region',
              valueColumn: 'instance_count',
              ariaLabel: 'Instances per region',
            });
          });
          loadTile('spread', SQL.spread, (columns, rows) => {
            renderRangeSpreadChart(document.getElementById('body-spread'), rowsToObjects(columns, rows));
          });
          refreshDurationProcessOptions();
          refreshDurationTile();
        }

        refreshAll();
        setInterval(() => {
          if (!document.hidden) {
            refreshAll();
          }
        }, REFRESH_MS);
        document.addEventListener('visibilitychange', () => {
          const stateEl = document.getElementById('refresh-state');
          if (document.hidden) {
            stateEl.textContent = 'paused (tab hidden)';
          } else {
            stateEl.textContent = 'refreshing every 10s';
            refreshAll();
          }
        });
      </script>
      </body>
      </html>
      """);

  /**
   * The full dashboard page: {@link #DASHBOARD_HTML_PART1} followed by {@link
   * #DASHBOARD_HTML_PART2}. A plain concatenation is safe here (won't itself be folded back into
   * one over-long constant) only because {@link #asIs(String)} already stopped both operands from
   * being "constant variables" -- see that method's javadoc.
   */
  private static final String DASHBOARD_HTML = DASHBOARD_HTML_PART1 + DASHBOARD_HTML_PART2;

  /**
   * The {@code /process-map} page: a BPMN heatmap over the raw {@code activities} table, rendered
   * with bpmn-js (loaded from a pinned CDN version -- see the {@code bpmn-js-script} tag below)
   * overlaid with per-element node stats (execution count + avg duration) from {@code
   * /api/process-map}. Degrades to a plain data-table view (no diagram) if the CDN script fails to
   * load or times out -- see {@code markBpmnJsUnavailable()} -- so an offline environment still
   * gets a readable page, and neither the dashboard nor the data explorer are affected either way
   * (this is an entirely separate page).
   */
  private static final String PROCESS_MAP_HTML =
      """
      <!doctype html>
      <html lang="en">
      <head>
      <meta charset="utf-8">
      <title>Analytics Lake -- Process Map</title>
      <link rel="stylesheet" href="https://unpkg.com/bpmn-js@17.11.1/dist/assets/diagram-js.css">
      <link rel="stylesheet" href="https://unpkg.com/bpmn-js@17.11.1/dist/assets/bpmn-font/css/bpmn-embedded.css">
      <style>
        :root {
          color-scheme: light dark;
          --page-bg: #f9f9f7;
          --surface: #fcfcfb;
          --ink-primary: #0b0b0b;
          --ink-secondary: #52514e;
          --ink-muted: #898781;
          --gridline: #e1e0d9;
          --border: rgba(11,11,11,0.10);
          --error: #d03b3b;
          --series-1: #2a78d6;
        }
        @media (prefers-color-scheme: dark) {
          :root {
            --page-bg: #0d0d0d;
            --surface: #1a1a19;
            --ink-primary: #ffffff;
            --ink-secondary: #c3c2b7;
            --ink-muted: #898781;
            --gridline: #2c2c2a;
            --border: rgba(255,255,255,0.10);
            --error: #e66767;
            --series-1: #3987e5;
          }
        }
        * { box-sizing: border-box; }
        body {
          margin: 0;
          padding: 1.5rem;
          font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
          background: var(--page-bg);
          color: var(--ink-primary);
        }
        h1 { margin: 0 0 0.25rem; font-size: 1.4rem; }
        p.subtitle { margin: 0 0 1.25rem; color: var(--ink-muted); font-size: 0.9rem; }
        p.subtitle a { color: var(--series-1); }

        .controls {
          display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap;
          margin-bottom: 1rem; padding: 0.85rem 1rem;
          background: var(--surface); border: 1px solid var(--border); border-radius: 8px;
        }
        .controls label { font-size: 0.8rem; color: var(--ink-secondary); }
        .controls select {
          background: var(--surface); color: var(--ink-primary);
          border: 1px solid var(--border); border-radius: 6px;
          padding: 0.3rem 0.5rem; font-size: 0.82rem; font-family: inherit;
        }
        .controls select:disabled { opacity: 0.5; }

        .hint {
          background: var(--surface); border: 1px solid var(--border); border-radius: 8px;
          padding: 1rem; margin-bottom: 1rem; font-size: 0.88rem; color: var(--ink-secondary);
        }
        .hint.error { color: var(--error); }
        .hint code { background: var(--gridline); border-radius: 4px; padding: 0.05rem 0.35rem; }
        .hint ul { margin: 0.5rem 0 0; padding-left: 1.2rem; }
        .hint.hidden { display: none; }

        #diagram-wrap { position: relative; }
        #canvas {
          height: 560px; background: var(--surface);
          border: 1px solid var(--border); border-radius: 8px;
        }
        #canvas.hidden { display: none; }
        #canvas .djs-palette { display: none; } /* read-only heatmap, no editing palette needed */

        .pm-badge {
          background: var(--surface); border: 1px solid var(--border); border-radius: 4px;
          padding: 1px 5px; font-size: 10px; line-height: 1.4; white-space: nowrap;
          box-shadow: 0 1px 3px rgba(0,0,0,0.18); color: var(--ink-primary);
          font-variant-numeric: tabular-nums;
        }

        details.data-table { margin-top: 0.75rem; }
        details.data-table summary { cursor: pointer; font-size: 0.8rem; color: var(--ink-muted); }
        details.data-table table {
          border-collapse: collapse; width: 100%; font-size: 0.78rem; margin-top: 0.5rem;
          font-variant-numeric: tabular-nums;
        }
        details.data-table th, details.data-table td {
          border: 1px solid var(--border); padding: 0.3rem 0.5rem; text-align: left; white-space: nowrap;
        }
        details.data-table th { position: sticky; top: 0; background: var(--surface); }
      </style>
      </head>
      <body>
      <h1>Process Map</h1>
      <p class="subtitle">
        BPMN heatmap over raw activity node stats.
        <a href="/dashboard">&larr; Back to the dashboard</a>
      </p>

      <div class="controls">
        <label for="process-select">Process</label>
        <select id="process-select" disabled><option value="">(loading…)</option></select>
      </div>

      <div class="hint hidden" id="pm-hint"></div>

      <div id="diagram-wrap">
        <div id="canvas" class="hidden"></div>
      </div>

      <details class="data-table" id="node-table-wrap" style="display:none">
        <summary>Node stats (execution count &middot; avg duration)</summary>
        <div id="node-table"></div>
      </details>

      <script src="https://unpkg.com/bpmn-js@17.11.1/dist/bpmn-navigated-viewer.production.min.js"
              onerror="window.__pmBpmnJsFailed = true;"></script>
      <script>
        // -------------------------------------------------------------------------------------------
        // bpmn-js availability: the <script> tag above sets window.__pmBpmnJsFailed on a network/404
        // error; a missing window.BpmnJS after a short grace period (CDN reachable but slow, or a
        // script that loaded but threw before defining the global) is treated the same way. Either
        // outcome degrades this page to its data-table fallback -- never a broken page, and never
        // anything that touches the dashboard or data-explorer pages (both are entirely separate
        // HTML documents this script has no reach into).
        // -------------------------------------------------------------------------------------------

        let viewer = null;
        let bpmnJsAvailable = false;

        function escapeHtml(s) {
          const div = document.createElement('div');
          div.textContent = s;
          return div.innerHTML;
        }

        function humanizeMs(ms) {
          if (ms === null || ms === undefined || Number.isNaN(ms)) {
            return '—';
          }
          const s = ms / 1000;
          if (s < 1) return Math.round(ms) + ' ms';
          if (s < 10) return s.toFixed(1) + ' s';
          if (s < 60) return Math.round(s) + ' s';
          const m = s / 60;
          if (m < 60) return m.toFixed(1) + ' min';
          return (m / 60).toFixed(1) + ' h';
        }

        function formatCount(n) {
          if (n === null || n === undefined || Number.isNaN(n)) return '—';
          return Math.round(n).toLocaleString('en-US');
        }

        function showHint(message, isError) {
          const hint = document.getElementById('pm-hint');
          hint.innerHTML = message;
          hint.classList.remove('hidden');
          hint.classList.toggle('error', !!isError);
        }

        function hideHint() {
          document.getElementById('pm-hint').classList.add('hidden');
        }

        function buildDataTable(container, columns, rows) {
          container.replaceChildren();
          if (rows.length === 0) {
            const p = document.createElement('p');
            p.textContent = '(no rows)';
            container.appendChild(p);
            return;
          }
          const table = document.createElement('table');
          const thead = document.createElement('thead');
          const headRow = document.createElement('tr');
          for (const col of columns) {
            const th = document.createElement('th');
            th.textContent = col;
            headRow.appendChild(th);
          }
          thead.appendChild(headRow);
          table.appendChild(thead);
          const tbody = document.createElement('tbody');
          for (const row of rows) {
            const tr = document.createElement('tr');
            for (const value of row) {
              const td = document.createElement('td');
              td.textContent = value === null || value === undefined ? '—' : String(value);
              tr.appendChild(td);
            }
            tbody.appendChild(tr);
          }
          table.appendChild(tbody);
          container.appendChild(table);
        }

        // -------------------------------------------------------------------------------------------
        // Catalog: populate the process picker, or explain why it's empty.
        // -------------------------------------------------------------------------------------------

        async function loadCatalog() {
          const select = document.getElementById('process-select');
          try {
            const response = await fetch('/api/process-map/catalog');
            const json = await response.json();
            if (!json.matchedProcesses || json.matchedProcesses.length === 0) {
              select.innerHTML = '<option value="">(no matching process)</option>';
              select.disabled = true;
              const dataIds = (json.dataProcessIds || []);
              const dirs = (json.scannedDirs || []);
              showHint(
                '<strong>No BPMN model matched any process id present in the data.</strong>' +
                '<p>Process ids seen in the data: ' +
                (dataIds.length ? dataIds.map((id) => '<code>' + escapeHtml(id) + '</code>').join(', ') : '(none yet)') +
                '</p><p>Directories scanned for <code>.bpmn</code> files:</p><ul>' +
                dirs.map((d) => '<li><code>' + escapeHtml(d) + '</code></li>').join('') +
                '</ul><p>Set the <code>lake.bpmnDir</code> system property to point at a directory ' +
                'containing a matching <code>.bpmn</code> file, or copy one into one of the ' +
                'directories above.</p>');
              return;
            }
            select.innerHTML = '';
            for (const process of json.matchedProcesses) {
              const option = document.createElement('option');
              option.value = process.processId;
              option.textContent = process.processName + ' (' + process.processId + ')';
              select.appendChild(option);
            }
            select.disabled = false;
            hideHint();
            await loadProcess(select.value);
          } catch (e) {
            showHint('Failed to load the process catalog: ' + escapeHtml(String(e)), true);
          }
        }

        // -------------------------------------------------------------------------------------------
        // Diagram rendering (bpmn-js) with a data-table fallback when it's unavailable.
        // -------------------------------------------------------------------------------------------

        function ensureViewer() {
          if (viewer || !bpmnJsAvailable) {
            return viewer;
          }
          viewer = new window.BpmnJS({ container: '#canvas' });
          return viewer;
        }

        async function loadProcess(processId) {
          if (!processId) {
            return;
          }
          try {
            const response = await fetch('/api/process-map/model?process=' + encodeURIComponent(processId));
            const json = await response.json();
            if (json.error) {
              showHint(escapeHtml(json.error), true);
              return;
            }
            if (bpmnJsAvailable) {
              const v = ensureViewer();
              document.getElementById('canvas').classList.remove('hidden');
              try {
                await v.importXML(json.bpmnXml);
                v.get('canvas').zoom('fit-viewport');
                hideHint();
              } catch (e) {
                document.getElementById('canvas').classList.add('hidden');
                showHint('Failed to render the BPMN diagram: ' + escapeHtml(String(e)), true);
              }
            } else {
              document.getElementById('canvas').classList.add('hidden');
              showHint(
                'Diagram renderer unavailable (offline, or the CDN script failed to load) -- ' +
                'showing node/edge stats as tables below instead.');
            }
            await loadData();
          } catch (e) {
            showHint('Failed to load the process model: ' + escapeHtml(String(e)), true);
          }
        }

        let appliedOverlayIds = []; // cleared before every re-render

        function clearDiagramAnnotations() {
          if (!viewer) {
            return;
          }
          const overlays = viewer.get('overlays');
          for (const id of appliedOverlayIds) {
            try { overlays.remove(id); } catch (e) { /* element may be gone after a re-import */ }
          }
          appliedOverlayIds = [];
        }

        function renderDiagramAnnotations(nodes) {
          if (!bpmnJsAvailable || !viewer) {
            return;
          }
          clearDiagramAnnotations();
          const elementRegistry = viewer.get('elementRegistry');
          const overlays = viewer.get('overlays');

          for (const node of nodes) {
            const element = elementRegistry.get(node.elementId);
            if (!element) {
              continue;
            }
            const html = '<div class="pm-badge">' + formatCount(node.executionCount) +
              '&times; &middot; ' + humanizeMs(node.avgDurationMs) + '</div>';
            const overlayId = overlays.add(node.elementId, { position: { bottom: -8, right: 0 }, html });
            appliedOverlayIds.push(overlayId);
          }
        }

        async function loadData() {
          const processId = document.getElementById('process-select').value;
          if (!processId) {
            return;
          }
          const query = 'process=' + encodeURIComponent(processId);
          try {
            const response = await fetch('/api/process-map?' + query);
            const json = await response.json();
            if (json.error) {
              showHint(escapeHtml(json.error), true);
              return;
            }

            document.getElementById('node-table-wrap').style.display = '';
            buildDataTable(document.getElementById('node-table'),
              ['element_id', 'execution_count', 'avg_duration_ms'],
              json.nodes.map((n) => [n.elementId, formatCount(n.executionCount), humanizeMs(n.avgDurationMs)]));

            renderDiagramAnnotations(json.nodes);
          } catch (e) {
            showHint('Failed to load process-map data: ' + escapeHtml(String(e)), true);
          }
        }

        document.getElementById('process-select').addEventListener('change', (evt) => {
          loadProcess(evt.target.value);
        });

        // Grace period for the CDN script: onerror fires immediately for a hard network/404
        // failure, but a slow-but-eventually-successful load needs a moment past DOMContentLoaded
        // before window.BpmnJS is defined -- 2.5s is generous for a same-machine demo stack.
        function detectBpmnJsAndStart() {
          bpmnJsAvailable = !window.__pmBpmnJsFailed && typeof window.BpmnJS !== 'undefined';
          loadCatalog();
        }
        window.setTimeout(detectBpmnJsAndStart, 2500);
      </script>
      </body>
      </html>
      """;
}

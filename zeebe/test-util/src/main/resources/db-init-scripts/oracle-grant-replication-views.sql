ALTER SESSION SET CONTAINER = camunda;
GRANT SELECT ON v_$database     TO camunda;
GRANT SELECT ON v_$archive_dest TO camunda;
GRANT SELECT ON v_$archive_dest_status TO camunda;
GRANT SELECT ON v_$archived_log TO camunda;

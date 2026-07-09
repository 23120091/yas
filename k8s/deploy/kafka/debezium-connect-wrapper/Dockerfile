FROM debezium/connect:2.7.3.Final AS debezium-plugins
FROM quay.io/strimzi/kafka:1.1.0-kafka-4.3.0
USER root
COPY --from=debezium-plugins /kafka/connect/debezium-connector-postgres/ /opt/kafka/plugins/debezium-connector-postgres/
USER 1001

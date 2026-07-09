FROM quay.io/strimzi/kafka:1.1.0-kafka-4.3.0 AS strimzi-libs
FROM debezium/connect:2.7.3.Final
USER root
COPY --from=strimzi-libs /opt/kafka/libs/kafka-kubernetes-config-provider-*.jar /kafka/libs/
COPY --from=strimzi-libs /opt/kafka/libs/kubernetes-client-*.jar /kafka/libs/
COPY --from=strimzi-libs /opt/kafka/libs/kubernetes-client-api-*.jar /kafka/libs/
COPY --from=strimzi-libs /opt/kafka/libs/kubernetes-httpclient-jdk-*.jar /kafka/libs/
COPY --from=strimzi-libs /opt/kafka/libs/kubernetes-model-*.jar /kafka/libs/
COPY --from=strimzi-libs /opt/kafka/libs/jackson-datatype-jsr310-*.jar /kafka/libs/
COPY --from=strimzi-libs /opt/kafka/libs/zjsonpatch-*.jar /kafka/libs/
COPY --from=strimzi-libs /opt/kafka/libs/snakeyaml-engine-*.jar /kafka/libs/
RUN mkdir -p /opt/kafka && \
    printf '#!/bin/bash\ncp -rn /kafka/config.orig/* /kafka/config/ 2>/dev/null || true\nln -sf log4j.properties /kafka/config/connect-log4j.properties 2>/dev/null || true\nexport KAFKA_LOG4J_OPTS="-Dlog4j.configuration=file:/kafka/config/log4j.properties"\nexec /kafka/bin/connect-distributed.sh /opt/kafka/custom-config/kafka-connect.properties\n' > /opt/kafka/kafka_connect_run.sh && \
    chmod +x /opt/kafka/kafka_connect_run.sh
USER 1001

FROM debezium/connect:2.7.3.Final
USER root
RUN mkdir -p /opt/kafka && \
    printf '#!/bin/bash\nexec /kafka/bin/connect-distributed.sh /opt/kafka/custom-config/kafka-connect.properties\n' > /opt/kafka/kafka_connect_run.sh && \
    chmod +x /opt/kafka/kafka_connect_run.sh
USER 1001

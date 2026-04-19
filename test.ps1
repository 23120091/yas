docker compose -f docker-compose.test.yml up -d java-env
docker exec -it yas-java-25 bash
./mvnw test -pl tax -am
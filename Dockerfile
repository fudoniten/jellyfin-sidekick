# syntax=docker/dockerfile:1
FROM clojure:temurin-21-tools-deps AS base

WORKDIR /app

COPY deps.edn ./
RUN clojure -P

COPY . .

EXPOSE 8080

CMD ["clojure", "-M:run"]

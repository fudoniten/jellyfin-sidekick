# syntax=docker/dockerfile:1
FROM clojure:temurin-21-tools-deps AS base

WORKDIR /app

COPY deps.edn ./
RUN clojure -P

COPY . .

EXPOSE 8080

# Run without --config, will use environment variables
CMD ["clojure", "-M:run"]

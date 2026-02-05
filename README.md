# Jellyfin Sidekick

A minimal Clojure service that works around Jellyfin's broken tag update API by writing NFO files and triggering refreshes.

## Why This Exists

Jellyfin's `POST /Items/{itemId}` API endpoint for updating metadata is fundamentally broken ([Issue #10724](https://github.com/jellyfin/jellyfin/issues/10724), closed as "Won't / Can't Fix"). This service provides a working alternative by:

1. Writing tags to `.nfo` files on the filesystem
2. Triggering Jellyfin's `POST /Items/{itemId}/Refresh` API to reload metadata from files

This enables programmatic tag management for ErsatzTV smart collections without database corruption.

## Features

* **NFO file generation** - Create/update `.nfo` files with tag data
* **Jellyfin integration** - Query Jellyfin for file paths and trigger refreshes
* **HTTP API** - Simple REST endpoint for tag updates
* **Nix & Docker ready** - Includes flake for development and deployContainer app

## Workflow

```
tunarr-scheduler (curate tags)
  ↓ POST /items/{itemId}/tags
jellyfin-sidekick (write .nfo files)
  ↓ POST /Items/{itemId}/Refresh
Jellyfin (reads .nfo, updates database)
  ↓ (sync)
ErsatzTV (smart collections with tags)
```

## Project Layout

```
├── resources/
│   └── config.edn          # service configuration
├── src/jellyfin_sidekick/
│   ├── main.clj            # CLI entry point
│   ├── config.clj          # config loading
│   ├── system.clj          # integrant system
│   ├── http/
│   │   ├── server.clj      # HTTP server
│   │   └── routes.clj      # API routes
│   ├── jellyfin/
│   │   ├── api.clj         # Jellyfin API client
│   │   └── nfo.clj         # NFO file generation
│   └── util/
│       └── logging.clj     # logging setup
```

## Getting Started

### Prerequisites

* [Clojure CLI tools](https://clojure.org/guides/install_clojure)
* Java 21+
* Access to Jellyfin API and media filesystem

### Configuration

Create a config file or use environment variables:

```clojure
{:log-level :info
 :server {:port 8080}
 :jellyfin {:base-url "http://jellyfin:8096"
            :api-key "your-api-key-here"}}
```

Environment variables:
- `PORT` - HTTP server port (default: 8080)
- `LOG_LEVEL` - Logging level (default: info)
- `JELLYFIN_URL` - Jellyfin base URL
- `JELLYFIN_API_KEY` - Jellyfin API key

### Running Locally

```bash
clojure -M:run --config resources/config.edn --log-level debug
```

Options:
- `--config PATH` - Path to configuration EDN file (can be specified multiple times)
- `--log-level LEVEL` - Set log level (trace, debug, info, warn, error)
- `--help` - Show usage information

### Docker

Build and run with Docker:

```bash
docker build -t jellyfin-sidekick .
docker run -p 8080:8080 \
  -e JELLYFIN_URL=http://jellyfin:8096 \
  -e JELLYFIN_API_KEY=your-key \
  -v /path/to/media:/media \
  jellyfin-sidekick
```

### Nix

Build with Nix:

```bash
nix build
```

Deploy container:

```bash
nix run .#deployContainer
```

## API Endpoints

### Update Item Tags

**Endpoint:** `POST /items/:itemId/tags`

**Description:** Update tags for a Jellyfin media item by writing an NFO file and triggering refresh.

**Request Body:**
```json
{
  "tags": ["SciFi", "ActionAdventure", "Espionage"]
}
```

**Response:**
```json
{
  "success": true,
  "itemId": "ff6bc8da-e37d-bddc-82e2-08d07e063472",
  "nfoPath": "/media/movies/Inception (2010)/Inception (2010).nfo",
  "refreshed": true
}
```

**Example:**
```bash
curl -X POST http://localhost:8080/items/ff6bc8da-e37d-bddc-82e2-08d07e063472/tags \
  -H "Content-Type: application/json" \
  -d '{"tags": ["SciFi", "ActionAdventure"]}'
```

### Health Check

**Endpoint:** `GET /healthz`

**Response:**
```json
{
  "status": "ok"
}
```

## Integration with tunarr-scheduler

The jellyfin-sidekick service is designed to work alongside tunarr-scheduler:

1. **tunarr-scheduler** manages tag curation and media catalog
2. **jellyfin-sidekick** writes tags to filesystem and triggers Jellyfin refresh
3. **Jellyfin** reads updated NFO files and updates its database
4. **ErsatzTV** syncs from Jellyfin and uses smart collections for scheduling

To integrate, update tunarr-scheduler's jellyfin-sync to call jellyfin-sidekick instead of using Jellyfin's broken update API.

## NFO File Format

The service generates NFO files following Kodi's format:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<movie>
  <title>Inception</title>
  <tag>SciFi</tag>
  <tag>ActionAdventure</tag>
  <tag>Espionage</tag>
</movie>
```

Tags are written in PascalCase to match Jellyfin conventions.

## Limitations

* **Filesystem access required** - The service needs write access to media file directories
* **NFO overwrites** - The service will overwrite existing NFO files (tags only - other metadata preserved)
* **Movie/episode support** - Currently supports movies and episodes (TV shows and music coming soon)

## Development

### Build

```bash
nix build
```

### Run locally

```bash
nix develop
clojure -M:run --config resources/config.edn
```

### Deploy

```bash
nix run .#deployContainer
```

## Troubleshooting

### Permission Denied Writing NFO

Ensure the service has write permissions to media directories:
```bash
chmod -R 755 /path/to/media
```

Or run the container with appropriate user/group:
```bash
docker run -u $(id -u):$(id -g) ...
```

### Tags Not Appearing in Jellyfin

1. Verify the NFO file was written correctly
2. Check Jellyfin refresh API response
3. Manually trigger library scan in Jellyfin UI
4. Check Jellyfin logs for NFO parsing errors

### Jellyfin API Errors

* **401 Unauthorized** - Check `JELLYFIN_API_KEY` is correct
* **404 Not Found** - Verify item ID exists and is accessible
* **Connection refused** - Check `JELLYFIN_URL` and network connectivity

## License

Same as tunarr-scheduler (specify your license here)

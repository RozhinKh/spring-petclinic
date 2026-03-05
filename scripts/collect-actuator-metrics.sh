#!/bin/bash
set -e

HOST=${1:-localhost}
PORT=${2:-8080}
OUTPUT_FILE=${3:-actuator-metrics.jsonl}
POLL_INTERVAL=${4:-5}
POLL_COUNT=${5:-120}

echo "=== Actuator Metrics Collection ==="
echo "Host: $HOST:$PORT"
echo "Interval: ${POLL_INTERVAL}s"
echo "Duration: $(( POLL_COUNT * POLL_INTERVAL ))s"
echo "Output: $OUTPUT_FILE"
echo ""

# Verify application is running
if command -v nc &> /dev/null; then
    if ! nc -z $HOST $PORT 2>/dev/null; then
        echo "Error: Application not running on $HOST:$PORT"
        exit 1
    fi
else
    # Fallback: use curl
    if ! curl -s --max-time 2 http://$HOST:$PORT/actuator/health > /dev/null 2>&1; then
        echo "Error: Application not running on $HOST:$PORT"
        exit 1
    fi
fi

# Clear output file
> "$OUTPUT_FILE"

echo "Starting metrics collection ($POLL_COUNT samples)..."
echo ""

# Collection loop
for i in $(seq 1 $POLL_COUNT); do
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ" 2>/dev/null || date -u +"%Y-%m-%dT%H:%M:%SZ")
    TIMESTAMP_UNIX=$(date +%s%3N 2>/dev/null || date +%s)000
    
    printf "[%3d/%3d] %s ... " "$i" "$POLL_COUNT" "$TIMESTAMP"
    
    # Collect metrics in parallel with timeout
    HTTP_REQUESTS=$(curl -s --max-time 3 \
        http://$HOST:$PORT/actuator/metrics/http.server.requests 2>/dev/null || echo "{}")
    
    JVM_MEMORY=$(curl -s --max-time 3 \
        http://$HOST:$PORT/actuator/metrics/jvm.memory.used?tag=area:heap 2>/dev/null || echo "{}")
    
    JVM_THREADS=$(curl -s --max-time 3 \
        http://$HOST:$PORT/actuator/metrics/jvm.threads.live 2>/dev/null || echo "{}")
    
    CPU_USAGE=$(curl -s --max-time 3 \
        http://$HOST:$PORT/actuator/metrics/process.cpu.usage 2>/dev/null || echo "{}")
    
    # Parse metrics (gracefully handle missing data)
    REQUEST_COUNT=$(echo "$HTTP_REQUESTS" | jq -r '.measurements[] | select(.statistic == "COUNT") | .value' 2>/dev/null | head -1 || echo "null")
    REQUEST_AVG=$(echo "$HTTP_REQUESTS" | jq -r '.measurements[] | select(.statistic == "MEAN") | .value' 2>/dev/null | head -1 || echo "null")
    MEMORY_VALUE=$(echo "$JVM_MEMORY" | jq -r '.measurements[0].value' 2>/dev/null || echo "null")
    THREADS_VALUE=$(echo "$JVM_THREADS" | jq -r '.measurements[0].value' 2>/dev/null || echo "null")
    CPU_VALUE=$(echo "$CPU_USAGE" | jq -r '.measurements[0].value' 2>/dev/null || echo "null")
    
    # Create JSON line
    JSON=$(cat <<EOF
{"timestamp":"$TIMESTAMP","timestamp_unix":$TIMESTAMP_UNIX,"http.requests.count":$REQUEST_COUNT,"http.requests.avg_ms":$REQUEST_AVG,"jvm.memory.heap.used_bytes":$MEMORY_VALUE,"jvm.threads.live":$THREADS_VALUE,"process.cpu.usage":$CPU_VALUE}
EOF
)
    
    echo "$JSON" >> "$OUTPUT_FILE"
    echo "✓"
    
    if [ $i -lt $POLL_COUNT ]; then
        sleep $POLL_INTERVAL
    fi
done

echo ""
echo "=== Collection Complete ==="
echo "Total samples: $POLL_COUNT"
echo "Output file: $OUTPUT_FILE"
echo "File size: $(ls -lh "$OUTPUT_FILE" | awk '{print $5}')"

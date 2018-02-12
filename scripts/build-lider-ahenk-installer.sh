#!/bin/bash

###
# This script builds the project & exports Lider Console for Linux (x32, x64), Windows (x32, x64) and MacOSX.
#
# Generated files can be found under /tmp/lider-ahenk-installer
###
set -e

pushd $(dirname $0) > /dev/null
PRJ_ROOT_PATH=$(dirname $(pwd -P))
popd > /dev/null
echo "Project path: $PRJ_ROOT_PATH"

# Generate third-party dependencies
echo "Generating third-party dependencies..."
cd "$PRJ_ROOT_PATH"/lider-ahenk-installer-dependencies
mvn clean p2:site
echo "Generated third-party dependencies."

# Start jetty server for Tycho to use generated dependencies
echo "Starting server for Tycho..."
mvn jetty:run &
J_PID=$!
echo "Started server."

# Build project
echo "Building lider-console project..."
cd "$PRJ_ROOT_PATH"
mvn clean install -DskipTests -Dtycho.disableP2Mirrors=true
echo "lider-ahenk-installer project build successfully."

# After exporting products, kill jetty server process
echo "Shutting down server..."
kill $J_PID
echo "Server shut down."

EXPORT_PATH=/tmp/lider-ahenk-installer
echo "Export path: $EXPORT_PATH"

# Copy resulting files
echo "Copying exported lider-ahenk-installer products to $EXPORT_PATH..."
mkdir -p "$EXPORT_PATH"
cp -rf "$PRJ_ROOT_PATH"/lider-ahenk-installer-product/target/products/. "$EXPORT_PATH"
echo "Copied exported lider-ahenk-installer products."

echo "Built finished successfully!"
echo "Files can be found under: $EXPORT_PATH"

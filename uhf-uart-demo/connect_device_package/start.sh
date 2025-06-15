#!/usr/bin/env bash
# stop script on error
set -e

# Check to see if root CA file exists, download if not
if [ ! -f ./root-CA.crt ]; then
  printf "\nDownloading AWS IoT Root CA certificate from AWS...\n"
  curl https://www.amazontrust.com/repository/AmazonRootCA1.pem > root-CA.crt
fi

# Check if certificate files exist
if [ ! -f ./c72.cert.pem ]; then
  printf "\nError: Certificate file c72.cert.pem not found!\n"
  printf "Please place your device certificate in the current directory.\n"
  exit 1
fi

if [ ! -f ./c72.private.key ]; then
  printf "\nError: Private key file c72.private.key not found!\n"
  printf "Please place your private key in the current directory.\n"
  exit 1
fi

# install AWS Device SDK for Java if not already installed
if [ ! -d ./aws-iot-device-sdk-java-v2 ]; then
  printf "\nInstalling AWS SDK...\n"
  git clone https://github.com/aws/aws-iot-device-sdk-java-v2.git --recursive
  cd aws-iot-device-sdk-java-v2
  
  # Use specific version instead of latest to avoid compatibility issues
  mvn clean install -Dmaven.test.skip=true
  cd ..
fi

# run pub/sub sample app using certificates downloaded in package
printf "\nRunning pub/sub sample application...\n"
cd aws-iot-device-sdk-java-v2

# Check if the sample exists
if [ ! -d "samples/BasicPubSub" ]; then
  printf "\nError: BasicPubSub sample not found!\n"
  exit 1
fi

mvn exec:java -pl samples/BasicPubSub \
  -Dexec.mainClass=pubsub.PubSub \
  -Dexec.args='--endpoint avt319l6989mq-ats.iot.us-east-2.amazonaws.com --client_id sdk-java --topic sdk/test/java --ca_file ../root-CA.crt --cert ../c72.cert.pem --key ../c72.private.key'
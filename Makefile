# Copyright (c) 2022 AccelByte Inc. All Rights Reserved.
# This is licensed software from AccelByte Inc, for limitations
# and restrictions contact your company contract manager.

IMAGE_NAME := mmgrpc-server-springboot

SHELL := /bin/bash

.PHONY: clean build image imagex

clean:
	docker run -t --rm -u $$(id -u):$$(id -g) -v $$(pwd):/data/ -w /data/ -e GRADLE_USER_HOME=.gradle gradle:7.5.1-jdk17 \
			gradle --console=plain -i --no-daemon clean

build:
	docker run -t --rm -u $$(id -u):$$(id -g) -v $$(pwd):/data/ -w /data/ -e GRADLE_USER_HOME=.gradle gradle:7.5.1-jdk17 \
			gradle --console=plain -i --no-daemon build

image:
	docker build -t ${IMAGE_NAME} .

imagex:
	trap "docker buildx rm ${IMAGE_NAME}-builder" EXIT \
			&& docker buildx create --name ${IMAGE_NAME}-builder --use \
			&& docker buildx build -t ${IMAGE_NAME} --platform linux/arm64/v8,linux/amd64 . \
			&& docker buildx build -t ${IMAGE_NAME} --load .

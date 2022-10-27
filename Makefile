# Copyright (c) 2022 AccelByte Inc. All Rights Reserved.
# This is licensed software from AccelByte Inc, for limitations
# and restrictions contact your company contract manager.

SHELL := /bin/bash

.PHONY: build

build:
	docker run -t --rm -u $$(id -u):$$(id -g) -v $$(pwd):/data/ -w /data/ -e GRADLE_USER_HOME=/data/.gradle gradle:7.5-jdk11 \
			gradle --console=plain -i --no-daemon build \
			$$([ -n "$$AB_NEXUS" ] && echo -PabNexusUsername="$$AB_NEXUS_USR" -PabNexusPassword="$$AB_NEXUS_PSW")	# Credentials from Jenkins (if any)

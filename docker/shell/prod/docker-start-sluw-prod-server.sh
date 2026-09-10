#!/bin/sh
export JAVA_HOME=/appuser/zulujdk1.17
export SERVER_PATH=/appuser/sluw-ai/servers
export TIME_ZONE='-Duser.timezone=GMT+8'
export LOGDIR=/appuser/logs

# 确保日志目录存在
mkdir -p ${LOGDIR}
#启动
echo $(date '+%Y-%m-%d %H:%M:%S.%3N') ${APPLICATION_NAME}.jar starting
exec ${JAVA_HOME}/bin/java ${JAVA_OPTS} ${TIME_ZONE} -jars ${SERVER_PATH}/${APPLICATION_NAME}.jar 1>${LOGDIR}/${APPLICATION_NAME}.log 2>&1
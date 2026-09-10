ARG BASE_IMAGE
FROM ${BASE_IMAGE}
LABEL maintainer="sluw"
WORKDIR /
ENV APPLICATION_NAME sluw-ai
ENV SLUWENV prod
ENV JAVA_OPTS="-Xms2048m -Xmx2048m"
ADD install/prod/servers/sluw-ai/sluw-ai.jar /appuser/sluw-ai/servers/
ADD docker/shell/prod/docker-start-sluw-prod-server.sh /appuser/sluw-ai/
USER root
RUN yum -y install tar
RUN yum -y install telnet
RUN chmod -R 777 /appuser/sluw-ai/ \
	&& chown -R 1000:1000 /appuser/sluw-ai/
EXPOSE 8089
USER appuser
ENTRYPOINT ["/appuser/sluw-ai/docker-start-sluw-prod-server.sh"]
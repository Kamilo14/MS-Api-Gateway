# Read Me First
The following was discovered as part of building this project:

* No Docker Compose services found. As of now, the application won't start! Please add at least one service to the `compose.yaml` file.
* The original package name 'MsApiGateway.catastrofecl-api-gateway' is invalid and this project uses 'MsApiGateway.catastrofecl_api_gateway' instead.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.14/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.14/maven-plugin/build-image.html)
* [Distributed Tracing Reference Guide](https://docs.micrometer.io/tracing/reference/index.html)
* [Getting Started with Distributed Tracing](https://docs.spring.io/spring-boot/3.5.14/reference/actuator/tracing.html)
* [Gateway](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/3.5.14/reference/actuator/index.html)
* [Docker Compose Support](https://docs.spring.io/spring-boot/3.5.14/reference/features/dev-services.html#features.dev-services.docker-compose)
* [Prometheus](https://docs.spring.io/spring-boot/3.5.14/reference/actuator/metrics.html#actuator.metrics.export.prometheus)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/3.5.14/reference/using/devtools.html)
* [Spring Reactive Web](https://docs.spring.io/spring-boot/3.5.14/reference/web/reactive.html)
* [Spring Configuration Processor](https://docs.spring.io/spring-boot/3.5.14/specification/configuration-metadata/annotation-processor.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
* [Building a Reactive RESTful Web Service](https://spring.io/guides/gs/reactive-rest-service/)

### Docker Compose support
This project contains a Docker Compose file named `compose.yaml`.

However, no services were found. As of now, the application won't start!

Please make sure to add at least one service in the `compose.yaml` file.

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.


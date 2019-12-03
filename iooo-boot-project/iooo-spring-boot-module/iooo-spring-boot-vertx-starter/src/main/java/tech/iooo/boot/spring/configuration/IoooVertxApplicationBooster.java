package tech.iooo.boot.spring.configuration;

import static tech.iooo.boot.spring.configuration.VertxConfigConstants.DEFAULT_DEPLOYMENT_OPTIONS;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;
import tech.iooo.boot.core.spring.component.SpringUtils;
import tech.iooo.boot.core.utils.ClassUtils;
import tech.iooo.boot.spring.annotation.VerticleDeploymentOption;

/**
 * Created on 2018/8/24 上午11:01
 *
 * @author <a href="mailto:yangkizhang@gmail.com?subject=iooo-spring-boot-vertx-bundle">Ivan97</a>
 */
@Configuration
public class IoooVertxApplicationBooster implements SmartLifecycle {

  private static final Logger logger = LoggerFactory.getLogger(IoooVertxApplicationBooster.class);
  @Autowired
  private Vertx vertx;

  @Autowired
  private IoooVertxProperties ioooVertxProperties;

  private boolean running;

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
    this.running = false;
  }

  @Override
  public void start() {
    SpringUtils.getBeansOfType(AbstractVerticle.class)
        .forEach((name, verticle) ->
            IoooVerticleServicesHolder.activeVerticleServices().put(verticle.getClass().getName(), "", verticle)
        );
    IoooVerticleServicesHolder.activeVerticleServices().values()
        .stream()
        .filter(verticle -> {
          if (ioooVertxProperties.getServer().isGatewayEnable()) {
            return true;
          } else {
            return !(verticle instanceof IoooGatewayVerticle);
          }
        }).forEach(verticle -> {
      Class<?> verticleClass = verticle.getClass();
      VerticleDeploymentOption verticleDeploymentOption = verticle.getClass().getAnnotation(VerticleDeploymentOption.class);
      String optionName;
      if (Objects.nonNull(verticleDeploymentOption)) {
        optionName = verticleDeploymentOption.value();
      } else {
        optionName = DEFAULT_DEPLOYMENT_OPTIONS;
      }
      DeploymentOptions deploymentOptions;
      if (ioooVertxProperties.getVerticle().isFailFast()) {
        deploymentOptions = SpringUtils.context().getBean(optionName, DeploymentOptions.class);
      } else {
        if (SpringUtils.context().containsBean(optionName)) {
          deploymentOptions = SpringUtils.context().getBean(optionName, DeploymentOptions.class);
        } else {
          logger.warn("failed to get deploymentOption [{}] during the deployment of verticle [{}],use default deployment options instead.",
              optionName, verticleClass.getSimpleName());
          deploymentOptions = SpringUtils.context().getBean(optionName, DeploymentOptions.class);
        }
      }
      vertx.deployVerticle(VertxConfigConstants.IOOO_VERTICLE_PREFIX + ":" + verticleClass.getName(),
          deploymentOptions,
          res -> {
            if (res.succeeded()) {
              if (logger.isInfoEnabled()) {
                String className = ClassUtils.getUserClass(verticleClass).getSimpleName();
                logger.info("deployed verticle [{}] with deploymentOption [{}],id [{}].", className, optionName, res.result());
              }
              IoooVerticleServicesHolder.activeVerticleServices().row(verticleClass.getName()).remove("");
              IoooVerticleServicesHolder.activeVerticleServices().row(verticleClass.getName()).put(res.result(), verticle);
            } else {
              logger.error("error with deploy verticle " + verticleClass.getName(), res.cause());
            }
          });
    });
    this.running = true;
  }

  @Override
  public void stop() {
    IoooVerticleServicesHolder.activeVerticleServices().columnKeySet().forEach(verticle -> vertx.undeploy(verticle, res -> {
      if (res.succeeded()) {
        if (logger.isInfoEnabled()) {
          logger.info("unload verticle {} ", verticle);
        }
      } else {
        logger.error("something happened while unload verticle " + verticle, res.cause());
      }
    }));
    IoooVerticleServicesHolder.reset();
    vertx.close();
  }

  @Override
  public boolean isRunning() {
    return this.running;
  }


}

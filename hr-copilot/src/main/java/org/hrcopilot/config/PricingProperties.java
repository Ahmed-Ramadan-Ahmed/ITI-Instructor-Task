package org.hrcopilot.config;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pricing")
@Getter
@Setter
public class PricingProperties {
    private Map<String, ModelPricing> models = new HashMap<>();

    public ModelPricing forModel(String model) {
        return models.getOrDefault(model, new ModelPricing());
    }
}

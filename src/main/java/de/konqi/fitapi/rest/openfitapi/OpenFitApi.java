package de.konqi.fitapi.rest.openfitapi;

import de.konqi.fitapi.rest.openfitapi.filter.AuthorizationRequestFilter;
import de.konqi.fitapi.rest.openfitapi.resources.FitnessActivities;
import de.konqi.fitapi.rest.openfitapi.resources.User;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import javax.ws.rs.ApplicationPath;
import java.util.logging.Logger;

/**
 * Created by konqi on 16.08.2015.
 */
@ApplicationPath("/openfitapi/api")
public class OpenFitApi extends ResourceConfig {
    private static final Logger LOGGER = Logger.getLogger(OpenFitApi.class.getName());
    public static final String SESSION_HEADER_NAME = "session";

    public OpenFitApi() {
        register(RolesAllowedDynamicFeature.class);

        register(AuthorizationRequestFilter.class);
        register(FitnessActivities.class);
        register(User.class);
        
        // Register an instance of LoggingFilter.
        register(new LoggingFilter(LOGGER, true));

        // Enable Tracing support.
        // property(ServerProperties.TRACING, "ALL");
    }


}

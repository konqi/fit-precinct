package de.konqi.fitapi.rest.webapi.resource;

import de.konqi.fitapi.Constants;
import de.konqi.fitapi.Utils;
import de.konqi.fitapi.auth.*;
import de.konqi.fitapi.db.repository.OAuthLoginRepository;
import de.konqi.fitapi.db.repository.SessionRepository;
import de.konqi.fitapi.db.repository.UserRepository;
import de.konqi.fitapi.rest.openfitapi.resources.Credential;
import de.konqi.fitapi.rest.webapi.WebApiUser;
import de.konqi.fitapi.rest.webapi.domain.LoginCallbackResponse;
import org.glassfish.jersey.server.mvc.Viewable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.Principal;
import java.util.Map;

/**
 * Created by konqi on 19.08.2015.
 */
@Path("/user")
@PermitAll
public class User {
    private static final Logger logger = LoggerFactory.getLogger(User.class);

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("user")
    public Response me(Credential credential, @Context HttpServletResponse response, @Context SecurityContext sc) {
//        sc.
        // .entity()
        Principal userPrincipal = sc.getUserPrincipal();
        return Response.ok().build();
    }

    @PUT
    @RolesAllowed("user")
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/uploadpw")
    public Response setUploadPassword(Map<String, String> params, @Context SecurityContext sc) {
        WebApiUser webApiUser = (WebApiUser) sc.getUserPrincipal();
        String password = params.get("password");
        logger.info("New password is: " + password);
        UserRepository.setUploadPassword(webApiUser, password);
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/login/{type}")
    public Response login(@PathParam("type") String type, @Context HttpServletRequest request) {
        StringBuilder sb = new StringBuilder();

        sb.append(request.getScheme()).append("://");
        sb.append(request.getServerName());
        if (request.getServerPort() != 80 && request.getServerPort() != 443)
            sb.append(":").append(request.getServerPort());
        sb.append(request.getRequestURI());
        sb.append("/oauth2callback");

        AuthUrlBuilder builder = AuthUrlBuilder.Builder(type);
        if (builder != null) {
            String state = CSRFTokenHandler.createToken(null);

            String authUrl = builder.withClientId(Constants.CLIENT_ID)
                    .withScope("openid email")
                    .withResponseType("code")
                    .withState(state)
                    .withRedirectUri(sb.toString()).build();

            return Response.seeOther(URI.create(authUrl)).build();
        }

        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @GET
    @Path("/login/{type}/oauth2callback")
    public Response loginCallback(@PathParam("type") String type,
                                  @QueryParam("state") String state,
                                  @QueryParam("code") String code,
                                  @QueryParam("authuser") Boolean authuser,
                                  @QueryParam("prompt") String prompt,
                                  @QueryParam("session_state") String sessionState,
                                  @Context HttpServletRequest request) {
        Object stateData = CSRFTokenHandler.getToken(state);
        if (stateData != null) {
            TokenRequestBuilder builder = TokenRequestBuilder.Builder(type);
            if (builder == null)
                return Response.status(Response.Status.BAD_REQUEST).build();

            String tokenRequestUrl = builder.withCode(code)
                    .withClientId(Constants.CLIENT_ID)
                    .withClientSecret(Constants.CLIENT_SECRET)
                    .withRedirectUri(request.getRequestURL().toString())
                    .withGrantType("authorization_code").build();

            try {
                URL url = new URL(builder.getBase());
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(urlConnection.getOutputStream()));
                bufferedWriter.write(tokenRequestUrl);
                bufferedWriter.close();

                if (urlConnection.getResponseCode() < 300) {
                    TokenResponse tokenResponse = Utils.jacksonObjectMapper.readValue(urlConnection.getInputStream(), TokenResponse.class);
                    IdClaim idClaim = TokenVerifierFacade.verify(type, tokenResponse.getIdToken());
                    if (idClaim == null) return Response.status(Response.Status.UNAUTHORIZED).build();
                    // FIXME tokenResponse.g

                    de.konqi.fitapi.db.domain.User user = OAuthLoginRepository.createUser(idClaim.getIsssuer(), idClaim.getSubscriber());
                    String sessionId = SessionRepository.createSession(user);
                    // Ref<de.konqi.fitapi.db.domain.User> userRef = OAuthLoginRepository.getLoginUser(idClaim.getIsssuer(), idClaim.getSubscriber());

                    // int maxAge = 60 * 60 * 24 * 7;
                    // NewCookie newCookie = new NewCookie("session", sessionId, "/web/api", request.getServerName(), null, maxAge, false);
                    LoginCallbackResponse loginCallbackResponse = new LoginCallbackResponse();
                    loginCallbackResponse.setSessionId(sessionId);
                    return Response.ok(new Viewable("/OAuth2ReturnServlet.jsp", loginCallbackResponse)).build();
                }
            } catch (IOException e) {
                logger.warn("Unable to exchange code for id_token.", e);
            }
        }

        return Response.status(Response.Status.UNAUTHORIZED).build();
    }



}

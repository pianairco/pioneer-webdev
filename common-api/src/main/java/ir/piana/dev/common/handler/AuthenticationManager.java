package ir.piana.dev.common.handler;

import ir.piana.dev.common.auth.AnonymousPrincipal;
import ir.piana.dev.common.auth.UserAuthentication;
import ir.piana.dev.common.util.SelfExpiringHashMap;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.UUID;

@Component
public class AuthenticationManager {
    private final SelfExpiringHashMap<String, UserAuthentication> selfExpiringHashMap =
            new SelfExpiringHashMap(900_000);

    private final SelfExpiringHashMap<String, SelfExpiringHashMap<String, Object>> selfExpiringSessionHashMap =
            new SelfExpiringHashMap(900_000);

    /*String add(Serializable principal, UserAuthorization userAuthorization) {
        UUID uuid = UUID.randomUUID();
        selfExpiringHashMap.put(uuid.toString(),
                new UserAuthentication(uuid, principal, userAuthorization));
        selfExpiringSessionHashMap.put(uuid.toString(), new HashMap<>());
        return uuid.toString();
    }*/

    void reassign(String uuid, Serializable principal, UserAuthorization userAuthorization) {
        selfExpiringHashMap.put(uuid,
                new UserAuthentication(UUID.fromString(uuid), principal, userAuthorization));
        selfExpiringSessionHashMap.put(uuid, selfExpiringSessionHashMap.remove(uuid.toString()));
    }

    void revoke(String uuid) {
        selfExpiringHashMap.put(uuid,
                new UserAuthentication(UUID.fromString(uuid), new AnonymousPrincipal("anonymous"), new UserAuthorization()));
        selfExpiringSessionHashMap.put(uuid, selfExpiringSessionHashMap.remove(uuid.toString()));
    }

    UserAuthentication getUserAuthentication(String uuid) {
        UserAuthentication userAuthentication = selfExpiringHashMap.get(uuid);
        if (userAuthentication != null) {
            selfExpiringHashMap.renewKey(uuid);
        } else {
            userAuthentication = new UserAuthentication(
                    UUID.fromString(uuid), new AnonymousPrincipal("anonymous"), new UserAuthorization());
            selfExpiringHashMap.put(uuid, userAuthentication);
        }
        return userAuthentication;
    }

    SelfExpiringHashMap<String, Object> getSession(String uuid) {
        SelfExpiringHashMap<String, Object> session = selfExpiringSessionHashMap.get(uuid);
        if (session != null) {
            selfExpiringSessionHashMap.renewKey(uuid);
        } else {
            session = new SelfExpiringHashMap<>();
            selfExpiringSessionHashMap.put(uuid, session);
        }
        return session;
    }

    public Serializable getPrincipal(String uuid) {
        UserAuthentication userAuthentication = getUserAuthentication(uuid);
        return userAuthentication != null ? userAuthentication.getPrincipal() : null;
    }
}

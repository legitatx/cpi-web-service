package me.legit.api.auth.mix;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class MixRegistrationText implements Handler {

    public MixRegistrationText() {
        //TODO
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json;charset=utf-8");
        ctx.result("{\"Status\":\"OK\",\"RegistrationText\":[{\"TextCode\":\"gtou_ppv2_proxy_create\",\"Text\":\"By creating an account, I agree to the <a target=\\\"_blank\\\" href=\\\"https://disneytermsofuse.com/english/\\\">Terms of Use</a> and acknowledge the <a target=\\\"_blank\\\" href=\\\"https://privacy.thewaltdisneycompany.com/en/current-privacy-policy/\\\">Privacy Policy</a>.\"}]}");
    }
}

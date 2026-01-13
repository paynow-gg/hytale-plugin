package gg.paynow.paynowhytale;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import org.jetbrains.annotations.NotNull;

public class PayNowCommand extends AbstractCommandCollection {

    public PayNowCommand() {
        super("paynow", "PayNow command");
        this.addSubCommand(new LinkCommand());
    }

    private class LinkCommand extends CommandBase {
        private final RequiredArg<String> tokenArg;

        private final PayNowHytale payNowHytale = PayNowHytale.getInstance();

        protected LinkCommand() {
            super("link", "Link your server to PayNow");
            this.tokenArg = this.withRequiredArg("token", "Token provided by PayNow", ArgTypes.STRING);
        }

        @Override
        protected void executeSync(@NotNull CommandContext ctx) {
            CommandUtil.requirePermission(ctx.sender(), HytalePermissions.fromCommand("paynow.admin"));
            this.payNowHytale.getPayNowLib().getConfig().setApiToken(this.tokenArg.get(ctx));
            this.payNowHytale.triggerConfigUpdate();
            ctx.sendMessage(Message.raw("API token updated"));
        }

    }
}
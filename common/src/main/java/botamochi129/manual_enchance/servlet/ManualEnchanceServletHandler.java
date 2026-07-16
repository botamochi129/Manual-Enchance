package botamochi129.manual_enchance.servlet;

import botamochi129.manual_enchance.util.SidingAccessor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mtr.data.*;
import mtr.libraries.javax.servlet.AsyncContext;
import mtr.libraries.javax.servlet.http.HttpServlet;
import mtr.libraries.javax.servlet.http.HttpServletRequest;
import mtr.libraries.javax.servlet.http.HttpServletResponse;
import mtr.servlet.Webserver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.io.PrintWriter;
import java.util.Set;

public class ManualEnchanceServletHandler extends HttpServlet {

    private static final String HTML;

    static {
        try {
            java.io.InputStream is = ManualEnchanceServletHandler.class.getResourceAsStream("/assets/manual_enchance/web/dispatch.html");
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            HTML = baos.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load dispatch.html", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
            sendHtml(resp, HTML);
        } else if (pathInfo.equals("/trains")) {
            handleTrains(req, resp);
        } else if (pathInfo.equals("/map")) {
            handleMap(req, resp);
        } else if (pathInfo.equals("/stations")) {
            handleStations(req, resp);
        } else if (pathInfo.equals("/routes")) {
            handleRoutes(req, resp);
        } else if (pathInfo.equals("/sidings")) {
            handleSidings(req, resp);
        } else {
            sendError(resp, 404, "Not found");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.equals("/reroute")) {
            handleReroute(req, resp);
        } else {
            sendError(resp, 404, "Not found");
        }
    }

    private void handleMap(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        Webserver.callback.accept(() -> {
            try {
                JsonObject result = new JsonObject();
                for (Level world : Webserver.getWorlds.get()) {
                    RailwayData data = RailwayData.getInstance(world);
                    if (data == null) continue;
                    JsonObject worldObj = new JsonObject();
                    worldObj.addProperty("dimension", world.dimension().location().toString());

                    JsonArray routesJson = new JsonArray();
                    for (Route route : data.routes) {
                        if (route.isHidden) continue;
                        JsonObject r = new JsonObject();
                        r.addProperty("id", route.id);
                        r.addProperty("name", route.name);
                        r.addProperty("color", route.color);
                        r.addProperty("transportMode", route.transportMode.name());
                        r.addProperty("circularState", route.circularState.name());
                        r.addProperty("isLightRail", route.isLightRailRoute);
                        if (route.isLightRailRoute) r.addProperty("number", route.lightRailRouteNumber);
                        JsonArray platforms = new JsonArray();
                        for (Route.RoutePlatform rp : route.platformIds) {
                            JsonObject rpObj = new JsonObject();
                            rpObj.addProperty("platformId", rp.platformId);
                            if (rp.customDestination != null && !rp.customDestination.isEmpty()) {
                                rpObj.addProperty("destination", rp.customDestination);
                            }
                            platforms.add(rpObj);
                        }
                        r.add("platformIds", platforms);
                        routesJson.add(r);
                    }
                    worldObj.add("routes", routesJson);

                    JsonArray stationsJson = new JsonArray();
                    for (Station station : data.stations) {
                        JsonObject s = new JsonObject();
                        s.addProperty("id", station.id);
                        s.addProperty("name", station.name);
                        s.addProperty("color", station.color);
                        s.addProperty("zone", station.zone);
                        BlockPos center = station.getCenter();
                        if (center != null) {
                            s.addProperty("x", center.getX());
                            s.addProperty("z", center.getZ());
                        }
                        s.addProperty("transportMode", station.transportMode.name());
                        stationsJson.add(s);
                    }
                    worldObj.add("stations", stationsJson);

                    JsonArray platformsJson = new JsonArray();
                    for (Platform platform : data.platforms) {
                        JsonObject p = new JsonObject();
                        p.addProperty("id", platform.id);
                        BlockPos mid = platform.getMidPos();
                        if (mid != null) {
                            p.addProperty("x", mid.getX());
                            p.addProperty("z", mid.getZ());
                        }
                        p.addProperty("axis", platform.getAxis().name());
                        Station st = data.dataCache.platformIdToStation.get(platform.id);
                        if (st != null) p.addProperty("stationId", st.id);
                        platformsJson.add(p);
                    }
                    worldObj.add("platforms", platformsJson);

                    result.add(world.dimension().location().toString(), worldObj);
                }
                sendAsyncResponse(resp, asyncContext, result.toString());
            } catch (Exception e) {
                sendAsyncResponse(resp, asyncContext, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }

    private void handleRoutes(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        Webserver.callback.accept(() -> {
            try {
                JsonArray result = new JsonArray();
                for (Level world : Webserver.getWorlds.get()) {
                    RailwayData data = RailwayData.getInstance(world);
                    if (data == null) continue;
                    for (Route route : data.routes) {
                        if (route.isHidden) continue;
                        JsonObject r = new JsonObject();
                        r.addProperty("id", route.id);
                        r.addProperty("name", route.name);
                        r.addProperty("color", route.color);
                        r.addProperty("transportMode", route.transportMode.name());
                        r.addProperty("circularState", route.circularState.name());
                        r.addProperty("isLightRail", route.isLightRailRoute);
                        if (route.isLightRailRoute) r.addProperty("number", route.lightRailRouteNumber);
                        JsonArray platforms = new JsonArray();
                        for (Route.RoutePlatform rp : route.platformIds) {
                            JsonObject rpObj = new JsonObject();
                            rpObj.addProperty("platformId", rp.platformId);
                            if (rp.customDestination != null && !rp.customDestination.isEmpty()) {
                                rpObj.addProperty("destination", rp.customDestination);
                            }
                            platforms.add(rpObj);
                        }
                        r.add("platformIds", platforms);
                        result.add(r);
                    }
                }
                sendAsyncResponse(resp, asyncContext, result.toString());
            } catch (Exception e) {
                sendAsyncResponse(resp, asyncContext, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }

    private void handleStations(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        Webserver.callback.accept(() -> {
            try {
                JsonArray result = new JsonArray();
                for (Level world : Webserver.getWorlds.get()) {
                    RailwayData data = RailwayData.getInstance(world);
                    if (data == null) continue;
                    for (Station station : data.stations) {
                        JsonObject s = new JsonObject();
                        s.addProperty("id", station.id);
                        s.addProperty("name", station.name);
                        s.addProperty("color", station.color);
                        s.addProperty("zone", station.zone);
                        BlockPos center = station.getCenter();
                        if (center != null) {
                            s.addProperty("x", center.getX());
                            s.addProperty("z", center.getZ());
                        }
                        s.addProperty("transportMode", station.transportMode.name());
                        JsonArray platforms = new JsonArray();
                        for (Platform p : data.platforms) {
                            Station ps = data.dataCache.platformIdToStation.get(p.id);
                            if (ps != null && ps.id == station.id) {
                                JsonObject pj = new JsonObject();
                                pj.addProperty("id", p.id);
                                BlockPos mid = p.getMidPos();
                                if (mid != null) {
                                    pj.addProperty("x", mid.getX());
                                    pj.addProperty("z", mid.getZ());
                                }
                                pj.addProperty("axis", p.getAxis().name());
                                platforms.add(pj);
                            }
                        }
                        s.add("platforms", platforms);
                        result.add(s);
                    }
                }
                sendAsyncResponse(resp, asyncContext, result.toString());
            } catch (Exception e) {
                sendAsyncResponse(resp, asyncContext, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }

    private void handleSidings(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        Webserver.callback.accept(() -> {
            try {
                JsonArray result = new JsonArray();
                for (Level world : Webserver.getWorlds.get()) {
                    RailwayData data = RailwayData.getInstance(world);
                    if (data == null) continue;
                    for (Siding siding : data.sidings) {
                        JsonObject s = new JsonObject();
                        s.addProperty("id", siding.id);
                        s.addProperty("name", siding.name);
                        s.addProperty("color", siding.color);
                        s.addProperty("transportMode", siding.transportMode.name());
                        BlockPos mid = siding.getMidPos();
                        if (mid != null) {
                            s.addProperty("x", mid.getX());
                            s.addProperty("z", mid.getZ());
                        }
                        s.addProperty("railLength", siding.railLength);
                        Set<TrainServer> trains = ((SidingAccessor) siding).getTrains();
                        s.addProperty("trainCount", trains != null ? trains.size() : 0);
                        result.add(s);
                    }
                }
                sendAsyncResponse(resp, asyncContext, result.toString());
            } catch (Exception e) {
                sendAsyncResponse(resp, asyncContext, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }

    private void handleTrains(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        Webserver.callback.accept(() -> {
            try {
                JsonArray trainsArray = new JsonArray();
                for (Level world : Webserver.getWorlds.get()) {
                    RailwayData data = RailwayData.getInstance(world);
                    if (data == null) continue;
                    for (Siding siding : data.sidings) {
                        Set<TrainServer> trains = ((SidingAccessor) siding).getTrains();
                        if (trains == null) continue;
                        BlockPos sidingPos = siding.getMidPos();
                        for (TrainServer train : trains) {
                            JsonObject obj = new JsonObject();
                            obj.addProperty("id", train.id);
                            obj.addProperty("sidingId", siding.id);
                            obj.addProperty("sidingName", siding.name);
                            obj.addProperty("trainId", train.trainId);
                            obj.addProperty("speed", train.getSpeed());
                            obj.addProperty("isOnRoute", train.getIsOnRoute());
                            obj.addProperty("railProgress", train.getRailProgress());
                            obj.addProperty("trainCars", train.trainCars);
                            obj.addProperty("spacing", train.spacing);
                            obj.addProperty("reversed", train.isReversed());
                            obj.addProperty("transportMode", train.transportMode.name());
                            if (sidingPos != null) {
                                obj.addProperty("x", sidingPos.getX());
                                obj.addProperty("z", sidingPos.getZ());
                            }
                            trainsArray.add(obj);
                        }
                    }
                }
                JsonObject result = new JsonObject();
                result.add("trains", trainsArray);
                sendAsyncResponse(resp, asyncContext, result.toString());
            } catch (Exception e) {
                sendAsyncResponse(resp, asyncContext, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }

    private void handleReroute(HttpServletRequest req, HttpServletResponse resp) {
        AsyncContext asyncContext = req.startAsync();
        Webserver.callback.accept(() -> {
            try {
                Gson gson = new Gson();
                JsonObject body = gson.fromJson(req.getReader(), JsonObject.class);
                long trainId = body.get("trainId").getAsLong();

                TrainServer targetTrain = null;
                Siding targetSiding = null;
                RailwayData targetData = null;
                MinecraftServer server = null;

                for (Level world : Webserver.getWorlds.get()) {
                    if (!(world instanceof ServerLevel serverLevel)) continue;
                    RailwayData data = RailwayData.getInstance(world);
                    if (data == null) continue;
                    for (Siding siding : data.sidings) {
                        for (TrainServer train : ((SidingAccessor) siding).getTrains()) {
                            if (train.id == trainId) {
                                targetTrain = train;
                                targetSiding = siding;
                                targetData = data;
                                server = serverLevel.getServer();
                                break;
                            }
                        }
                        if (targetTrain != null) break;
                    }
                    if (targetTrain != null) break;
                }

                JsonObject result = new JsonObject();
                if (targetTrain == null || targetSiding == null) {
                    result.addProperty("success", false);
                    result.addProperty("error", "Train not found");
                } else {
                    targetData.railwayDataPathGenerationModule.generatePath(server, targetSiding.id);
                    result.addProperty("success", true);
                    result.addProperty("message", "Rerouting train " + trainId + " (" + targetSiding.name + ")");
                }
                sendAsyncResponse(resp, asyncContext, result.toString());
            } catch (Exception e) {
                sendAsyncResponse(resp, asyncContext, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }

    private void sendAsyncResponse(HttpServletResponse resp, AsyncContext asyncContext, String json) {
        try {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter writer = resp.getWriter();
            writer.write(json);
            writer.flush();
            asyncContext.complete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendHtml(HttpServletResponse resp, String html) {
        try {
            resp.setContentType("text/html");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter writer = resp.getWriter();
            writer.write(html);
            writer.flush();
        } catch (Exception ignored) {
        }
    }

    private void sendError(HttpServletResponse resp, int code, String message) {
        try {
            resp.setStatus(code);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            JsonObject err = new JsonObject();
            err.addProperty("error", message);
            PrintWriter writer = resp.getWriter();
            writer.write(err.toString());
            writer.flush();
        } catch (Exception ignored) {
        }
    }
}

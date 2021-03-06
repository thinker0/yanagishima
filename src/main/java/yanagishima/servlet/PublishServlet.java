package yanagishima.servlet;

import me.geso.tinyorm.TinyORM;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yanagishima.config.YanagishimaConfig;
import yanagishima.row.Publish;
import yanagishima.util.AccessControlUtil;
import yanagishima.util.HttpRequestUtil;
import yanagishima.util.JsonUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;

@Singleton
public class PublishServlet extends HttpServlet {

    private static Logger LOGGER = LoggerFactory
            .getLogger(PublishServlet.class);

    private static final long serialVersionUID = 1L;

    @Inject
    private TinyORM db;

    private YanagishimaConfig yanagishimaConfig;

    @Inject
    public PublishServlet(YanagishimaConfig yanagishimaConfig) {
        this.yanagishimaConfig = yanagishimaConfig;
    }

    @Override
    protected void doPost(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {

        HashMap<String, Object> retVal = new HashMap<String, Object>();

        try {
            String datasource = HttpRequestUtil.getParam(request, "datasource");
            if(yanagishimaConfig.isCheckDatasource()) {
                if(!AccessControlUtil.validateDatasource(request, datasource)) {
                    try {
                        response.sendError(SC_FORBIDDEN);
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            String userName = request.getHeader(yanagishimaConfig.getAuditHttpHeaderName());
            String engine = HttpRequestUtil.getParam(request, "engine");
            String queryid = Optional.ofNullable(request.getParameter("queryid")).get();
            Optional<Publish> publishOptional = db.single(Publish.class).where("datasource=? and engine=? and query_id=?", datasource, engine, queryid).execute();
            if(publishOptional.isPresent()) {
                retVal.put("publish_id", publishOptional.get().getPublishId());
            } else {
                String publish_id = DigestUtils.md5Hex(datasource + ";" + engine + ";" + queryid);
                db.insert(Publish.class)
                        .value("publish_id", publish_id)
                        .value("datasource", datasource)
                        .value("engine", engine)
                        .value("query_id", queryid)
                        .value("user", userName)
                        .execute();
                retVal.put("publish_id", publish_id);
            }
        } catch (Throwable e) {
            LOGGER.error(e.getMessage(), e);
            retVal.put("error", e.getMessage());
        }

        JsonUtil.writeJSON(response, retVal);

    }

}

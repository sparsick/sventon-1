/*
 * ====================================================================
 * Copyright (c) 2005-2008 sventon project. All rights reserved.
 *
 * This software is licensed as described in the file LICENSE, which
 * you should have received as part of this distribution. The terms
 * are also available at http://sventon.berlios.de.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package de.berlios.sventon.web.ctrl;

import de.berlios.sventon.model.LogEntryBundle;
import de.berlios.sventon.web.command.SVNBaseCommand;
import de.berlios.sventon.web.model.UserRepositoryContext;
import org.apache.commons.lang.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * Controller for showing logs.
 * <p/>
 * The log entries will be paged if the number of entries exceeds max page size, {@link #pageSize}.
 *
 * @author patrikfr@users.berlios.de
 * @author jesper@users.berlios.de
 */
public final class ShowLogController extends AbstractSVNTemplateController implements Controller {

  /**
   * Max entries per page, default set to 20.
   */
  private int pageSize = 20;

  /**
   * Set page size.
   * Max number of log entires shown at a time.
   *
   * @param pageSize Page size.
   */
  public void setPageSize(final int pageSize) {
    this.pageSize = pageSize;
  }

  /**
   * {@inheritDoc}
   */
  protected ModelAndView svnHandle(final SVNRepository repository, final SVNBaseCommand svnCommand,
                                   final long headRevision, final UserRepositoryContext userRepositoryContext,
                                   final HttpServletRequest request, final HttpServletResponse response,
                                   final BindException exception) throws Exception {

    final String nextPathParam = ServletRequestUtils.getStringParameter(request, "nextPath", svnCommand.getPath());
    final SVNRevision nextRevParam = SVNRevision.parse(ServletRequestUtils.getStringParameter(request, "nextRevision", "HEAD"));

    final long revNumber;
    if (SVNRevision.HEAD.equals(nextRevParam)) {
      revNumber = headRevision;
    } else {
      revNumber = nextRevParam.getNumber();
    }

    final List<LogEntryBundle> logEntryBundles = new ArrayList<LogEntryBundle>();

    logger.debug("Assembling logs data [" + revNumber + " - " + FIRST_REVISION + "] limit: " + pageSize);

    // TODO: Safer parsing would be nice.
    final List<SVNLogEntry> logEntries = getRepositoryService().getRevisions(
        svnCommand.getName(), repository, revNumber, FIRST_REVISION, nextPathParam, pageSize);

    String pathAtRevision = nextPathParam;

    for (final SVNLogEntry logEntry : logEntries) {
      logEntryBundles.add(new LogEntryBundle(logEntry, pathAtRevision));
      //noinspection unchecked
      final Map<String, SVNLogEntryPath> allChangedPaths = logEntry.getChangedPaths();
      final Set<String> changedPaths = allChangedPaths.keySet();
      for (String entryPath : changedPaths) {
        int i = StringUtils.indexOfDifference(entryPath, pathAtRevision);
        if (i == -1) { // Same path
          final SVNLogEntryPath logEntryPath = allChangedPaths.get(entryPath);
          if (logEntryPath.getCopyPath() != null) {
            pathAtRevision = logEntryPath.getCopyPath();
          }
        } else if (entryPath.length() == i) { // Part path, can be a branch
          final SVNLogEntryPath logEntryPath = allChangedPaths.get(entryPath);
          if (logEntryPath.getCopyPath() != null) {
            pathAtRevision = logEntryPath.getCopyPath() + pathAtRevision.substring(i);
          }
        }
      }
    }

    final Map<String, Object> model = new HashMap<String, Object>();

    model.put("logEntriesPage", logEntryBundles);
    model.put("pageSize", pageSize);
    model.put("isFile", getRepositoryService().getNodeKind(repository, svnCommand.getPath(), svnCommand.getRevisionNumber()) == SVNNodeKind.FILE);
    model.put("morePages", logEntryBundles.size() == pageSize);
    return new ModelAndView("showLog", model);
  }
}
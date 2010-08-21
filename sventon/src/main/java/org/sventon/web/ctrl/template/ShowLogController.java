/*
 * ====================================================================
 * Copyright (c) 2005-2010 sventon project. All rights reserved.
 *
 * This software is licensed as described in the file LICENSE, which
 * you should have received as part of this distribution. The terms
 * are also available at http://www.sventon.org.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.sventon.web.ctrl.template;

import org.apache.commons.lang.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.servlet.ModelAndView;
import org.sventon.NoSuchRevisionException;
import org.sventon.SVNConnection;
import org.sventon.SventonException;
import org.sventon.model.*;
import org.sventon.web.command.BaseCommand;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/**
 * Controller for showing logs.
 * <p/>
 * The log entries will be paged if the number of entries exceeds max page size, {@link #pageSize}.
 *
 * @author patrik@sventon.org
 * @author jesper@sventon.org
 */
public final class ShowLogController extends AbstractTemplateController {

  /**
   * Max entries per page, default set to 20.
   */
  private int pageSize = 20;

  /**
   * Set page size.
   * Max number of log entries shown at a time.
   *
   * @param pageSize Page size.
   */
  public void setPageSize(final int pageSize) {
    this.pageSize = pageSize;
  }

  @Override
  protected ModelAndView svnHandle(final SVNConnection connection, final BaseCommand command,
                                   final long headRevision, final UserRepositoryContext userRepositoryContext,
                                   final HttpServletRequest request, final HttpServletResponse response,
                                   final BindException exception) throws Exception {

    final String nextPath = ServletRequestUtils.getStringParameter(request, "nextPath", command.getPath());
    final Revision nextRevision = Revision.parse(ServletRequestUtils.getStringParameter(
        request, "nextRevision", command.getRevision().toString()));
    final boolean stopOnCopy = ServletRequestUtils.getBooleanParameter(request, "stopOnCopy", true);
    final long fromRevision = calculateFromRevision(headRevision, nextRevision);

    final List<LogEntry> logEntries = new ArrayList<LogEntry>();
    try {
      logEntries.addAll(getRepositoryService().getLogEntries(command.getName(), connection,
          fromRevision, Revision.FIRST.getNumber(), nextPath, pageSize, stopOnCopy, true));

      String pathAtRevision = nextPath;

      for (final LogEntry logEntry : logEntries) {
        logEntry.setPathAtRevision(pathAtRevision);

        //noinspection unchecked
        final Set<ChangedPath> allChangedPaths = logEntry.getChangedPaths();

        for (ChangedPath entryPath : allChangedPaths) {
          int i = StringUtils.indexOfDifference(entryPath.getPath(), pathAtRevision);
          if (i == -1) { // Same path
            if (entryPath.getCopyPath() != null) {
              pathAtRevision = entryPath.getCopyPath();
            }
          } else if (entryPath.getPath().length() == i) { // Part path, can be a branch
            if (entryPath.getCopyPath() != null) {
              pathAtRevision = entryPath.getCopyPath() + pathAtRevision.substring(i);
            }
          }
        }
      }
    } catch (NoSuchRevisionException nsre) {
      logger.info(nsre.getMessage());
    } catch (SventonException ex) {
      logger.error(ex.getMessage());
    }

    final Map<String, Object> model = new HashMap<String, Object>();
    final DirEntry.Kind nodeKind = getRepositoryService().getNodeKind(connection, command.getPath(), command.getRevisionNumber());
    model.put("stopOnCopy", stopOnCopy);
    model.put("logEntriesPage", logEntries);
    model.put("pageSize", pageSize);
    model.put("isFile", nodeKind == DirEntry.Kind.FILE);
    model.put("morePages", logEntries.size() == pageSize);
    model.put("nextPath", nextPath);
    model.put("nextRevision", fromRevision);
    return new ModelAndView(getViewName(), model);
  }

  protected long calculateFromRevision(long headRevision, Revision nextRevision) {
    final long fromRevision;
    if (nextRevision.isHeadRevision()) {
      fromRevision = headRevision;
    } else {
      fromRevision = nextRevision.getNumber();
    }
    return fromRevision;
  }
}

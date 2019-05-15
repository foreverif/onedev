package io.onedev.server.web.page.project.setting.branchprotection;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.eclipse.jgit.lib.ObjectId;

import de.agilecoders.wicket.core.markup.html.bootstrap.common.NotificationPanel;
import io.onedev.server.ci.CISpec;
import io.onedev.server.ci.CISpecAware;
import io.onedev.server.model.Project;
import io.onedev.server.model.support.BranchProtection;
import io.onedev.server.web.editable.BeanContext;
import io.onedev.server.web.page.project.ProjectPage;

@SuppressWarnings("serial")
abstract class BranchProtectionEditPanel extends Panel implements CISpecAware {

	private final BranchProtection protection;
	
	public BranchProtectionEditPanel(String id, BranchProtection protection) {
		super(id);
		
		this.protection = protection;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		setOutputMarkupId(true);
		
		Form<?> form = new Form<Void>("form");
		form.add(new NotificationPanel("feedback", form));
		
		form.add(BeanContext.edit("editor", protection));
			
		form.add(new AjaxButton("save") {

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				super.onSubmit(target, form);
				onSave(target, protection);
			}

			@Override
			protected void onError(AjaxRequestTarget target, Form<?> form) {
				super.onError(target, form);
				target.add(form);
			}
			
		});
		form.add(new AjaxLink<Void>("cancel") {

			@Override
			public void onClick(AjaxRequestTarget target) {
				onCancel(target);
			}
			
		});
		add(form);
	}

	@Override
	public CISpec getCISpec() {
		Project project = ((ProjectPage) getPage()).getProject();
		ObjectId commitId = project.getObjectId(project.getDefaultBranch(), true);
		return project.getCISpec(commitId);
	}

	protected abstract void onSave(AjaxRequestTarget target, BranchProtection protection);
	
	protected abstract void onCancel(AjaxRequestTarget target);
}

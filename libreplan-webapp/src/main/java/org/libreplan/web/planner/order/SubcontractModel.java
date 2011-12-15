/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2011 Igalia, S.L.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.libreplan.web.planner.order;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.joda.time.LocalDate;
import org.libreplan.business.common.exceptions.ValidationException;
import org.libreplan.business.externalcompanies.daos.IExternalCompanyDAO;
import org.libreplan.business.externalcompanies.entities.ExternalCompany;
import org.libreplan.business.planner.daos.ISubcontractedTaskDataDAO;
import org.libreplan.business.planner.entities.SubcontractState;
import org.libreplan.business.planner.entities.SubcontractedTaskData;
import org.libreplan.business.planner.entities.SubcontractorDeliverDate;
import org.libreplan.business.planner.entities.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zkoss.ganttz.data.GanttDate;

/**
 * Model for UI operations related with subcontract process and
 * {@link SubcontractedTaskData} entity.
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 * @author Susana Montes Pedreira <smontes@wirelessgalicia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SubcontractModel implements ISubcontractModel {

    /**
     * Conversation state
     */
    private Task task;
    private org.zkoss.ganttz.data.Task ganttTask;
    private Date startDate;
    private Date endDate;
    private SubcontractedTaskData subcontractedTaskData;

    private SubcontractedTaskData currentSubcontractedTaskData;

    @Autowired
    private IExternalCompanyDAO externalCompanyDAO;

    @Autowired
    private ISubcontractedTaskDataDAO subcontractedTaskDataDAO;

    @Override
    @Transactional(readOnly = true)
    public void init(Task task, org.zkoss.ganttz.data.Task ganttTask) {
        this.task = task;
        this.startDate = task.getStartDate();
        this.endDate = task.getEndDate();

        this.ganttTask = ganttTask;

        SubcontractedTaskData subcontractedTaskData = task
                .getSubcontractedTaskData();

        this.currentSubcontractedTaskData = subcontractedTaskData;

        if (subcontractedTaskData == null) {
            this.subcontractedTaskData = SubcontractedTaskData.create(task);
            this.addDeliverDate(getEndDate());
        } else {
            subcontractedTaskDataDAO.reattach(subcontractedTaskData);
            loadRequiredDeliveringDates(subcontractedTaskData);
            this.subcontractedTaskData = SubcontractedTaskData
                    .createFrom(subcontractedTaskData);
        }
    }

    private void loadRequiredDeliveringDates(SubcontractedTaskData subcontractedTaskData){
        if(subcontractedTaskData != null){
            for (SubcontractorDeliverDate subDeliverDate : subcontractedTaskData
                    .getRequiredDeliveringDates()) {
                subDeliverDate.getSaveDate();
            }
        }
    }

    @Override
    public SubcontractedTaskData getSubcontractedTaskData() {
        return subcontractedTaskData;
    }

    @Override
    @Transactional(readOnly = true)
    public void confirm() throws ValidationException {
        if (task != null) {
            if (subcontractedTaskData == null) {
                task.setSubcontractedTaskData(null);
            } else {
                subcontractedTaskDataDAO.save(subcontractedTaskData);

                if (currentSubcontractedTaskData == null) {
                    task.setSubcontractedTaskData(subcontractedTaskData);
                } else {
                    currentSubcontractedTaskData
                            .applyChanges(subcontractedTaskData);
                }

                task.removeAllSatisfiedResourceAllocations();
                Task.convertOnStartInFixedDate(task);
            }

            recalculateTaskLength();
        }
    }

    private void recalculateTaskLength() {
        GanttDate previousStartDate = ganttTask.getBeginDate();
        GanttDate previousEnd = ganttTask.getEndDate();

        task.setStartDate(startDate);
        task.setEndDate(endDate);

        ganttTask.fireChangesForPreviousValues(previousStartDate, previousEnd);
    }

    @Override
    public void cancel() {
        task = null;
        startDate = null;
        endDate = null;
        currentSubcontractedTaskData = null;
        subcontractedTaskData = null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExternalCompany> getSubcontractorExternalCompanies() {
        return externalCompanyDAO.findSubcontractor();
    }

    @Override
    public void setExternalCompany(ExternalCompany externalCompany) {
        if (subcontractedTaskData != null) {
            subcontractedTaskData.setExternalCompany(externalCompany);
        }
    }

    @Override
    public boolean hasResourceAllocations() {
        if (task != null) {
            return !task.getSatisfiedResourceAllocations().isEmpty();
        }
        return false;
    }

    @Override
    public Date getEndDate() {
        return endDate;
    }

    @Override
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    @Override
    public void removeSubcontractedTaskData() {
        subcontractedTaskData = null;
    }

    @Override
    public SortedSet<SubcontractorDeliverDate> getDeliverDates(){
        if(subcontractedTaskData != null){
            return subcontractedTaskData.getRequiredDeliveringDates();
        }
        return new TreeSet<SubcontractorDeliverDate>();
    }

    @Override
    public void addDeliverDate(Date subDeliverDate){
        if(subcontractedTaskData != null){
            SubcontractorDeliverDate subcontractorDeliverDate = SubcontractorDeliverDate
                    .create(new Date(), subDeliverDate, null);
            subcontractedTaskData
                    .addRequiredDeliveringDates(subcontractorDeliverDate);

            //update the end date of the task
            updateEndDateWithDeliverDate();
        }
    }

    private void updateEndDateWithDeliverDate(){
        SubcontractorDeliverDate lastDeliverDate = this
                .getSubcontractedTaskData().getRequiredDeliveringDates().last();
        task.setEndDate(lastDeliverDate.getSubcontractorDeliverDate());
    }

    @Override
    public boolean alreadyExistsRepeatedDeliverDate(Date newDeliverDate){
        for(SubcontractorDeliverDate subDeliverDate : this
        .getSubcontractedTaskData().getRequiredDeliveringDates()){
            Date deliverDate = subDeliverDate.getSubcontractorDeliverDate();
            Date currentDeliverDate = new LocalDate(deliverDate).toDateTimeAtStartOfDay().toDate();
            newDeliverDate = new LocalDate(newDeliverDate).toDateTimeAtStartOfDay().toDate();
            if(currentDeliverDate.compareTo(newDeliverDate) == 0){
                return true;
            }
        }
        return false;
    }

    @Override
    public void removeRequiredDeliverDate(
            SubcontractorDeliverDate subcontractorDeliverDate) {
        if(subcontractedTaskData != null){
            subcontractedTaskData.removeRequiredDeliveringDates(subcontractorDeliverDate);
        }
    }

}

/*
*  Power BI Visual CLI
*
*  Copyright (c) Microsoft Corporation
*  All rights reserved.
*  MIT License
*
*  Permission is hereby granted, free of charge, to any person obtaining a copy
*  of this software and associated documentation files (the ""Software""), to deal
*  in the Software without restriction, including without limitation the rights
*  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
*  copies of the Software, and to permit persons to whom the Software is
*  furnished to do so, subject to the following conditions:
*
*  The above copyright notice and this permission notice shall be included in
*  all copies or substantial portions of the Software.
*
*  THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
*  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
*  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
*  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
*  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
*  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
*  THE SOFTWARE.
*/
"use strict";

import powerbi from "powerbi-visuals-api";
import { FormattingSettingsService } from "powerbi-visuals-utils-formattingmodel";
import "./../style/visual.less";
/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-ignore - bpmn-js doesn't have proper TypeScript definitions for the Viewer export
import BpmnViewer from "bpmn-js/lib/Viewer";
/* eslint-enable @typescript-eslint/ban-ts-comment */

import VisualConstructorOptions = powerbi.extensibility.visual.VisualConstructorOptions;
import VisualUpdateOptions = powerbi.extensibility.visual.VisualUpdateOptions;
import IVisual = powerbi.extensibility.visual.IVisual;

import { VisualFormattingSettingsModel } from "./settings";

// Default BPMN XML to render
const DEFAULT_BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>`;

export class Visual implements IVisual {
    private target: HTMLElement;
    private bpmnContainer: HTMLElement;
    private bpmnViewer: any;
    private formattingSettings: VisualFormattingSettingsModel;
    private formattingSettingsService: FormattingSettingsService;

    constructor(options: VisualConstructorOptions) {
        console.log('BPMN Visual constructor', options);
        this.formattingSettingsService = new FormattingSettingsService();
        this.target = options.element;
        
        if (document) {
            // Create container for BPMN diagram
            this.bpmnContainer = document.createElement("div");
            this.bpmnContainer.className = "bpmn-container";
            this.bpmnContainer.style.width = "100%";
            this.bpmnContainer.style.height = "100%";
            this.target.appendChild(this.bpmnContainer);
            
            // Initialize BPMN viewer
            this.bpmnViewer = new BpmnViewer({
                container: this.bpmnContainer,
                width: '100%',
                height: '100%'
            });
            
            // Render default BPMN
            this.renderBpmn(DEFAULT_BPMN_XML);
        }
    }

    private async renderBpmn(xml: string) {
        try {
            await this.bpmnViewer.importXML(xml);
            
            // Fit diagram to viewport
            const canvas = this.bpmnViewer.get('canvas');
            canvas.zoom('fit-viewport');
            
            console.log('BPMN diagram rendered successfully');
        } catch (err) {
            console.error('Error rendering BPMN diagram:', err);
            
            // Display error message using DOM API instead of innerHTML
            if (this.bpmnContainer) {
                // Clear container
                while (this.bpmnContainer.firstChild) {
                    this.bpmnContainer.removeChild(this.bpmnContainer.firstChild);
                }
                
                // Create error div
                const errorDiv = document.createElement('div');
                errorDiv.style.padding = '20px';
                errorDiv.style.color = 'red';
                
                const errorTitle = document.createElement('h3');
                errorTitle.textContent = 'Error rendering BPMN diagram';
                errorDiv.appendChild(errorTitle);
                
                const errorMessage = document.createElement('p');
                errorMessage.textContent = err instanceof Error ? err.message : String(err);
                errorDiv.appendChild(errorMessage);
                
                this.bpmnContainer.appendChild(errorDiv);
            }
        }
    }

    public update(options: VisualUpdateOptions) {
        this.formattingSettings = this.formattingSettingsService.populateFormattingSettingsModel(
            VisualFormattingSettingsModel, 
            options.dataViews?.[0]
        );

        console.log('BPMN Visual update', options);
        
        // Check if data is provided
        const dataViews = options.dataViews;
        if (dataViews && dataViews.length > 0 && dataViews[0].categorical) {
            const categorical = dataViews[0].categorical;
            
            // Try to get BPMN XML from data
            if (categorical.categories && categorical.categories.length > 0) {
                const category = categorical.categories[0];
                if (category.values && category.values.length > 0) {
                    const bpmnXml = category.values[0] as string;
                    
                    // Check if it looks like XML
                    if (bpmnXml && typeof bpmnXml === 'string' && bpmnXml.trim().startsWith('<?xml')) {
                        this.renderBpmn(bpmnXml);
                        return;
                    }
                }
            }
        }
        
        // Resize container if viewport changed
        if (this.bpmnViewer && options.viewport) {
            const canvas = this.bpmnViewer.get('canvas');
            canvas.zoom('fit-viewport');
        }
    }

    /**
     * Returns properties pane formatting model content hierarchies, properties and latest formatting values, Then populate properties pane.
     * This method is called once every time we open properties pane or when the user edit any format property. 
     */
    public getFormattingModel(): powerbi.visuals.FormattingModel {
        return this.formattingSettingsService.buildFormattingModel(this.formattingSettings);
    }
}
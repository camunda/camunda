# BPMN Coverage

Elements marked in orange are currently implemented by Zeebe.

<div class="bpmn-symbols">

  <div>
    <div class="bpmn-symbol-group">
      <h3>Participants</h3>
      <div style="position: relative">
        <div class="bpmn-symbol-container implemented">
          <svg height="90" version="1.1" width="130" xmlns="http://www.w3.org/2000/svg">
            <rect x="5" y="5" width="120" height="80" r="0" rx="0" ry="0" fill="none" stroke="#333333" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1"></rect>
            <text x="15" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" transform="matrix(0,-1,1,0,-30.0156,59.9844)" font-size="12px" font-family="Arial, Helvetica, sans-serif">
              <tspan dy="4">Pool</tspan>
            </text>
          </svg>
        </div>
        <div style="position: absolute; top: 0; left: 24px; z-index: 2" class="bpmn-symbol-container implemented">
          <svg height="90" version="1.1" width="106" xmlns="http://www.w3.org/2000/svg">
            <rect x="5" y="5" width="96" height="80" r="0" rx="0" ry="0" fill="none" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
            <text x="15" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif; color:black;" transform="matrix(0,-1,1,0,-30.0078,59.9922)" font-size="12px" font-family="Arial, Helvetica, sans-serif">
              <tspan dy="4">Lane</tspan>
            </text>
          </svg>
        </div>
      </div>
    </div>
  </div>
  <div>
    <div class="bpmn-symbol-group">
      <h3>Subprocesses</h3>
        <a href="/bpmn-workflows/embedded-subprocesses/embedded-subprocesses.html">
          <div class="bpmn-symbol-container implemented">
            <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.375px;">
              <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
              <rect x="49" y="73" width="12" height="12" r="0" rx="0" ry="0" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
              <path fill="none" stroke="#333333" d="M50,71V77M47,74H53" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
              <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
                <tspan dy="4">Subprocess</tspan>
              </text>
            </svg>
            <span class="fa fa-link bpmn-symbol-link"></span>
          </div>
        </a>
        <a href="/bpmn-workflows/call-activities/call-activities.html">
            <div class="bpmn-symbol-container implemented">
            <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.25px;">
              <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
              <rect x="49" y="73" width="12" height="12" r="0" rx="0" ry="0" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
              <path fill="none" stroke="#333333" d="M50,71V77M47,74H53" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
              <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
                <tspan dy="4">Call Activity</tspan>
              </text>
            </svg>
            <span class="fa fa-link bpmn-symbol-link"></span>
          </div>
        </a>
      <a href="/bpmn-workflows/event-subprocesses/event-subprocesses.html">
        <div class="bpmn-symbol-container implemented">
          <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.125px;">
            <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="2,2" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
            <rect x="49" y="73" width="12" height="12" r="0" rx="0" ry="0" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
            <path fill="none" stroke="#333333" d="M50,71V77M47,74H53" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
              <tspan dy="-3.1953125">Event</tspan>
              <tspan dy="14.399999999999999" x="55">Subprocess</tspan>
            </text>
          </svg>
					<span class="fa fa-link bpmn-symbol-link"></span>
        </div>
      </a>
      <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <rect x="8" y="8" width="94" height="74" r="3" rx="3" ry="3" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <rect x="49" y="73" width="12" height="12" r="0" rx="0" ry="0" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <path fill="none" stroke="#333333" d="M50,71V77M47,74H53" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">Transaction</tspan>
          </text>
        </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
      </div>
    </div>
  </div>
  <div>
    <div class="bpmn-symbol-group">
      <h3>Tasks</h3>
      <a href="/bpmn-workflows/service-tasks/service-tasks.html">
        <div class="bpmn-symbol-container implemented">
          <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg">
            <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
            <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
              <tspan dy="4">Service Task</tspan>
            </text>
            <path fill="#ffffff" stroke="#333333" d="M20.347,4.895L17.786,7.455L18.729000000000003,9.732L22.353,9.732L22.353,13.114999999999998L18.731,13.114999999999998L17.788,15.392L20.351,17.955L17.958,20.346999999999998L15.396999999999998,17.785999999999998L13.119999999999997,18.729L13.119999999999997,22.352999999999998L9.736999999999998,22.352999999999998L9.736999999999998,18.730999999999998L7.46,17.788L4.897,20.35L2.506,17.958L5.066,15.397L4.124,13.12L0.49999999999999956,13.12L0.49999999999999956,9.736999999999998L4.1209999999999996,9.736999999999998L5.0649999999999995,7.4609999999999985L2.5029999999999997,4.897999999999998L4.895,2.505999999999998L7.455,5.065999999999998L9.732,4.124999999999998L9.732,0.4999999999999982L13.116,0.4999999999999982L13.116,4.120999999999999L15.392,5.063999999999998L17.954,2.5019999999999984Z" stroke-width="1.4999999999999998" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.8,0,0,0.8,7.2853,7.2853)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            <path fill="#ffffff" stroke="#333333" d="M15.141,11.426C15.141,13.477185,13.478186,15.14,11.427,15.14C9.3758145,15.14,7.7130000999999995,13.477185,7.7130000999999995,11.426C7.7130000999999995,9.3748141,9.375814499999999,7.7119997,11.427,7.7119997C13.478185999999999,7.7119997,15.141,9.3748141,15.141,11.426Z" stroke-width="1.4999999999999998" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.8,0,0,0.8,7.2854,7.2852)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            <path fill="#ffffff" stroke="#333333" d="M26.347,10.895L23.786,13.455L24.729000000000003,15.732L28.353,15.732L28.353,19.115L24.731,19.115L23.788,21.392L26.351,23.955L23.958,26.346999999999998L21.397,23.785999999999998L19.119999999999997,24.729L19.119999999999997,28.352999999999998L15.736999999999998,28.352999999999998L15.736999999999998,24.730999999999998L13.459999999999997,23.787999999999997L10.896999999999997,26.349999999999998L8.505999999999997,23.958L11.065999999999997,21.397L10.123999999999997,19.119999999999997L6.4999999999999964,19.119999999999997L6.4999999999999964,15.736999999999998L10.120999999999997,15.736999999999998L11.064999999999998,13.460999999999999L8.502999999999998,10.897999999999998L10.894999999999998,8.505999999999998L13.454999999999998,11.065999999999999L15.732,10.124999999999998L15.732,6.499999999999998L19.116,6.499999999999998L19.116,10.120999999999999L21.392,11.063999999999998L23.954,8.501999999999999Z" stroke-width="1.4999999999999998" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.8,0,0,0.8,8.4853,8.4853)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            <path fill="#ffffff" stroke="#333333" d="M21.141,17.426001C21.141,19.477186,19.478185999999997,21.140000999999998,17.427,21.140000999999998C15.375814,21.140000999999998,13.713,19.477186,13.713,17.426001C13.713,15.374815,15.375813999999998,13.712000999999999,17.427,13.712000999999999C19.478186,13.712000999999999,21.141,15.374814999999998,21.141,17.426001Z" stroke-width="1.4999999999999998" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.8,0,0,0.8,8.4854,8.4852)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
          <span class="fa fa-link bpmn-symbol-link"></span>
        </div>
      </a>
      <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.875px;">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">User Task</tspan>
          </text>
          <path fill="#f4f6f7" stroke="#333333" d="M6.0095,22.5169H22.8676V17.0338C22.8676,17.0338,21.2345,14.2919,17.9095,13.4169H11.434500000000002C8.342600000000001,14.35,5.951400000000001,17.4419,5.951400000000001,17.4419L6.009500000000001,22.5169Z" stroke-width="0.69999999" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <path fill="none" stroke="#333333" d="M9.8,19.6L9.8,22.400000000000002" stroke-width="0.69999999" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <path fill="#333333" stroke="#333333" d="M19.6,19.6L19.6,22.400000000000002" stroke-width="0.69999999" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <circle cx="19.5" cy="13.5" r="5" fill="#333333" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.75,0,0,0.75,4.875,3.375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
          <path fill="#f0eff0" stroke="#333333" d="M11.2301,10.5581C11.2301,10.5581,13.1999,8.8599,14.9933,9.293199999999999C16.7867,9.726499999999998,18.2301,8.8081,18.2301,8.8081C18.4051,9.9897,18.2595,11.4331,17.2095,12.716899999999999C17.2095,12.716899999999999,17.967599999999997,13.2419,17.967599999999997,13.7669C17.967599999999997,14.2919,18.055099999999996,15.0794,17.267599999999998,15.8669C16.480099999999997,16.6544,13.417599999999998,16.7419,12.542599999999998,15.8669C11.667599999999998,14.9919,11.667599999999998,14.5838,11.667599999999998,14C11.667599999999998,13.4162,12.075699999999998,13.125,12.542599999999998,12.6581C11.784499999999998,12.25,10.793299999999999,10.9956,11.230099999999998,10.5581Z" stroke-width="0.69999999" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
        </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
      </div>
      <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.75px;">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">Script Task</tspan>
          </text>
          <path fill="#ffffff" stroke="#333333" d="M6.402,0.5H20.902C20.902,0.5,15.069,3.333,15.069,6.083S19.486,12.083,19.486,15.25S15.319,20.333,15.319,20.333H0.235C0.235,20.333,5.235,17.665999999999997,5.235,15.332999999999998S0.6520000000000001,8.582999999999998,0.6520000000000001,6.082999999999998S6.402,0.5,6.402,0.5ZM3.5,4.5L13.5,4.5M3.8,8.5L13.8,8.5M6.3,12.5L16.3,12.5M6.5,16.5L16.5,16.5" stroke-width="1.6666666666666667" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.6,0,0,0.6,9.2274,9.1666)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
        </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
      </div>
      <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.625px;">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="-3.1953125">Business Rule</tspan>
            <tspan dy="14.399999999999999" x="55">Task</tspan>
          </text>
          <rect x="10" y="9" width="17" height="12" r="0" rx="0" ry="0" fill="#ffffff" stroke="#333333" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <rect x="10" y="9" width="17" height="4" r="0" rx="0" ry="0" fill="#333333" stroke="#333333" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <path fill="none" stroke="#333333" d="M2,10L19,10M7,4L7,14" stroke-width="1" stroke-linecap="butt" stroke-linejoin="butt" stroke-opacity="1" transform="matrix(1,0,0,1,8,7)" style="stroke-linecap: butt; stroke-linejoin: round; stroke-opacity: 1;"></path>
        </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
      </div>
      <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.5px;">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">Manual Task</tspan>
          </text>
          <path fill="#ffffff" stroke="#333333" d="M0.5,3.751L4.583,0.5009999999999999C4.583,0.5009999999999999,15.749,0.5839999999999999,16.666,0.5839999999999999S14.249,3.5009999999999994,15.166,3.5009999999999994S26.833,3.5009999999999994,27.75,3.5009999999999994C28.916,5.209,27.582,6.667999999999999,26.916,7.167999999999999S27.791,9.084999999999999,25.916,11.584999999999999C25.166,11.834999999999999,26.666,13.459999999999999,24.583000000000002,14.918C23.416,15.501,25.166,16.46,23.333000000000002,17.750999999999998C22.166,17.750999999999998,2.5000000000000036,17.833999999999996,2.5000000000000036,17.833999999999996L0.5000000000000036,16.500999999999998V3.751ZM13.5,7L27,7M13.5,11L26,11M14,14.5L25,14.5M8.2,3.1L15,3.1" stroke-width="1.6666666666666667" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.6,0,0,0.6,10.7422,8.667)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
        </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
      </div>
      <a href="/bpmn-workflows/receive-tasks/receive-tasks.html">
        <div class="bpmn-symbol-container implemented">
          <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.375px;">
            <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
            <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
              <tspan dy="4">Receive Task</tspan>
            </text>
            <path fill="#ffffff" stroke="#333333" d="M7,10L7,20L23,20L23,10ZM7,10L15,16L23,10" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
          <span class="fa fa-link bpmn-symbol-link"></span>
        </div>
      </a>
      <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.25px;">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">Undefined Task</tspan>
          </text>
        </svg>
      </div>
      <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">Send Task</tspan>
          </text>
          <path fill="#333333" stroke="none" d="M7,9L15,15L23,9ZM7,10L7,20L23,20L23,10L15,16Z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,3,1)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
        </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
      </div>
      <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.875px;">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="-3.1953125">Receive Task</tspan>
            <tspan dy="14.399999999999999" x="55">(instantiated)</tspan>
          </text>
          <circle cx="20" cy="20" r="12" fill="#ffffff" stroke="#333333" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
          <path fill="#ffffff" stroke="#333333" d="M7,10L7,20L23,20L23,10ZM7,10L15,16L23,10" stroke-width="1" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
        </svg>
      </div>
    </div>
  </div>
  <div>
    <div class="bpmn-symbol-group">
      <h3>Gateways</h3>
      <a href="/bpmn-workflows/exclusive-gateways/exclusive-gateways.html">
        <div class="bpmn-symbol-container implemented">
          <svg height="60" version="1.1" width="60" xmlns="http://www.w3.org/2000/svg">
            <path fill="#ffffff" stroke="#333333" d="M5,25L25,5L45,25L25,45L5,25" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            <text x="13" y="55" text-anchor="start" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: start; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
              <tspan dy="4">XOR</tspan>
            </text>
            <path fill="#333333" stroke="#333333" d="M13.25,12.0625L18.5,20.5L13.25,28.9375L17.25,28.9375L20.5,23.6875L23.75,28.9375L27.65625,28.9375L22.4375,20.5L27.65625,12.0625L23.75,12.0625L20.5,17.3125L17.25,12.0625L13.25,12.0625Z" stroke-opacity="1" stroke-width="1" transform="matrix(1,0,0,1,5,5)" style="stroke-opacity: 1;"></path>
          </svg>
          <span class="fa fa-link bpmn-symbol-link"></span>
        </div>
      </a>
      <div class="bpmn-symbol-container">
        <svg height="60" version="1.1" width="60" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.875px;">
          <path fill="#ffffff" stroke="#333333" d="M5,25L25,5L45,25L25,45L5,25" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <text x="15" y="55" text-anchor="start" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: start; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">OR</tspan>
          </text>
          <circle cx="25" cy="25" r="9.428571428571429" fill="none" stroke="#333333" stroke-opacity="1" stroke-width="3" style="stroke-opacity: 1;"></circle>
        </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
      </div>
      <a href="/bpmn-workflows/parallel-gateways/parallel-gateways.html">
        <div class="bpmn-symbol-container implemented">
          <svg height="60" version="1.1" width="60" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.75px;">
            <path fill="#ffffff" stroke="#333333" d="M5,25L25,5L45,25L25,45L5,25" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            <text x="13" y="55" text-anchor="start" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: start; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
              <tspan dy="4">AND</tspan>
            </text>
            <path fill="none" stroke="#333333" d="M11.25,20.5L30.25,20.5M20.5,11.25L20.5,30.25" stroke-opacity="1" stroke-width="4" transform="matrix(1,0,0,1,5,5)" style="stroke-opacity: 1;"></path>
          </svg>
          <span class="fa fa-link bpmn-symbol-link"></span>
        </div>
      </a>
      <a href="/bpmn-workflows/event-based-gateways/event-based-gateways.html">
	      <div class="bpmn-symbol-container implemented">
	        <svg height="60" version="1.1" width="60" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.625px;">
	          <path fill="#ffffff" stroke="#333333" d="M5,25L25,5L45,25L25,45L5,25" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
	          <text x="11" y="55" text-anchor="start" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: start; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
	            <tspan dy="4">Event</tspan>
	          </text>
	          <circle cx="25" cy="25" r="12.121212121212121" fill="none" stroke="#333333" stroke-opacity="1" stroke-width="1" style="stroke-opacity: 1;"></circle>
	          <circle cx="25" cy="25" r="10.121212121212121" fill="none" stroke="#333333" stroke-opacity="1" stroke-width="1" style="stroke-opacity: 1;"></circle>
	          <path fill="none" stroke="#333333" d="M24.827514,26.844972L15.759248000000001,26.844216L12.957720300000002,18.219549L20.294545,12.889969L27.630481000000003,18.220774L24.827514,26.844972Z" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
	        </svg>
	        <span class="fa fa-link bpmn-symbol-link"></span>
	      </div>
      </a>
      <div class="bpmn-symbol-container">
        <svg height="60" version="1.1" width="60" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.875px;">
          <path fill="#ffffff" stroke="#333333" d="M5,25L25,5L45,25L25,45L5,25" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <text x="1" y="55" text-anchor="start" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: start; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">Complex</tspan>
          </text>
          <g transform="scale(0.75) translate(7.5, 7.5)">
            <path d="m 23,13 0,7.116788321167883 -5.018248175182482,-5.018248175182482 -3.102189781021898,3.102189781021898 5.018248175182482,5.018248175182482 -7.116788321167883,0 0,4.37956204379562 7.116788321167883,0  -5.018248175182482,5.018248175182482 l 3.102189781021898,3.102189781021898 5.018248175182482,-5.018248175182482 0,7.116788321167883 4.37956204379562,0 0,-7.116788321167883 5.018248175182482,5.018248175182482 3.102189781021898,-3.102189781021898 -5.018248175182482,-5.018248175182482 7.116788321167883,0 0,-4.37956204379562 -7.116788321167883,0 5.018248175182482,-5.018248175182482 -3.102189781021898,-3.102189781021898 -5.018248175182482,5.018248175182482 0,-7.116788321167883 -4.37956204379562,0 z" style="fill: black; stroke-width: 1px; stroke: black;"></path>
          </g>
        </svg>
      </div>
    </div>
    <div class="bpmn-symbol-group">
      <h3>Data</h3>
      <div class="bpmn-symbol-container">
        <svg height="100" version="1.1" width="60" xmlns="http://www.w3.org/2000/svg">
          <path fill="none" stroke="#333333" d="M5,5L45,5L55,15L55,65L5,65L5,5M45,5L45,15L55,15" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <text x="30" y="35" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="-3.1953125">Data </tspan>
            <tspan dy="14.399999999999999" x="30">Object</tspan>
          </text>
        </svg>
      </div>
      <div class="bpmn-symbol-container">
        <svg height="115" version="1.1" width="70" xmlns="http://www.w3.org/2000/svg">
          <path fill="none" stroke="#333333" d="M30.708999999999985,0C50.72199999999999,0,62.00099999999999,3.05,62.00099999999999,5.729C62.00099999999999,8.407,62.00099999999999,50.824999999999996,62.00099999999999,53.973C62.00099999999999,57.121,45.58099999999999,60.173,30.61299999999999,60.173C15.644999999999989,60.173,-1.0658141036401503e-14,57.218,-1.0658141036401503e-14,53.875C-1.0658141036401503e-14,50.533,-1.0658141036401503e-14,8.146999999999998,-1.0658141036401503e-14,5.825000000000003C-1.4210854715202004e-14,3.503,10.696999999999985,0,30.708999999999985,0M62.00099999999999,15.027999999999999C62.00099999999999,17.014,58.38099999999999,21.579,30.73399999999999,21.579C3.0879999999999903,21.579,-1.0658141036401503e-14,16.893,-1.0658141036401503e-14,15.125M-1.4210854715202004e-14,10.475000000000001C-1.4210854715202004e-14,12.244000000000002,3.087999999999986,16.93,30.733999999999988,16.93C58.380999999999986,16.93,62.00099999999999,12.364999999999998,62.00099999999999,10.379M-1.4210854715202004e-14,5.825000000000001C-1.4210854715202004e-14,8.175,3.087999999999986,12.280000000000001,30.733999999999988,12.280000000000001C58.380999999999986,12.280000000000001,62.00099999999999,8.368000000000002,62.00099999999999,5.7280000000000015M62.00099999999999,5.729000000000001V10.573M0.0239999999999857,5.729000000000001V10.573M62.00099999999999,10.379000000000001V15.223000000000003M0.0239999999999857,10.379000000000001V15.223000000000003" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <text x="35" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="-3.1953125">Data </tspan>
            <tspan dy="14.399999999999999" x="35">Store</tspan>
          </text>
        </svg>
      </div>
    </div>
    <div class="bpmn-symbol-group">
      <h3>Artifacts</h3>
      <div class="bpmn-symbol-container">
        <svg height="110" version="1.1" width="70" xmlns="http://www.w3.org/2000/svg">
          <path fill="none" stroke="#333333" d="M15,5L5,5L5,65L15,65" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          <text x="10" y="35" text-anchor="start" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: start; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="-3.1953125">Text</tspan>
            <tspan dy="14.399999999999999" x="10"></tspan>
            <tspan dy="14.399999999999999" x="10">Annotation</tspan>
          </text>
        </svg>
      </div>
      <div class="bpmn-symbol-container">
        <svg height="70" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg">
          <rect x="5" y="5" width="100" height="60" r="5" rx="5" ry="5" fill="none" stroke="#333333" stroke-width="2" stroke-opacity="1" stroke-dasharray="8,6,2,6" style="stroke-opacity: 1;"></rect>
          <text x="10" y="15" text-anchor="start" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: start; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">Group</tspan>
          </text>
        </svg>
      </div>
    </div>
  </div>
</div>

<div>
    <div class="bpmn-symbol-group">
    <h3>Markers</h3>
    <a href="/bpmn-workflows/multi-instance/multi-instance.html">
      <div class="bpmn-symbol-container implemented">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.375px;">
          <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
          <path fill="none" stroke="#333333"  d="m44,60 m 3,2 l 0,10 m 3,-10 l 0,10 m 3,-10 l 0,10" data-marker="parallel" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"/>
          <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
            <tspan dy="4">Multi-Instance</tspan>
          </text>
        </svg>
        <span class="fa fa-link bpmn-symbol-link"></span>
      </div>
    </a>
    <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.375px;">
              <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
              <path fill="none" stroke="#333333" d="m 50,73 c 3.526979,0 6.386161,-2.829858 6.386161,-6.320661 0,-3.490806 -2.859182,-6.320661 -6.386161,-6.320661 -3.526978,0 -6.38616,2.829855 -6.38616,6.320661 0,1.745402 0.714797,3.325567 1.870463,4.469381 0.577834,0.571908 1.265885,1.034728 2.029916,1.35457 l -0.718163,-3.909793 m 0.718163,3.909793 -3.885211,0.802902" data-marker="loop" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
              <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
                <tspan dy="4">Loop</tspan>
              </text>
            </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
    </div>
    <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.375px;">
              <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
              <path fill="none" stroke="#333333" d="m 44,67 7,-5 0,10 z m 7.1,-0.3 6.9,-4.7 0,10 -6.9,-4.7 z" data-marker="compensation" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
              <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
                <tspan dy="4">Compensation</tspan>
              </text>
            </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
    </div>
    <div class="bpmn-symbol-container">
        <svg height="90" version="1.1" width="110" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.375px;">
              <rect x="5" y="5" width="100" height="80" r="5" rx="5" ry="5" fill="#ffffff" stroke="#333333" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></rect>
              <path fill="none" stroke="#333333" d="m 44,65 m 0.84461,2.64411 c 1.05533,-1.23780996 2.64337,-2.07882 4.29653,-1.97997996 2.05163,0.0805 3.85579,1.15803 5.76082,1.79107 1.06385,0.34139996 2.24454,0.1438 3.18759,-0.43767 0.61743,-0.33642 1.2775,-0.64078 1.7542,-1.17511 0,0.56023 0,1.12046 0,1.6807 -0.98706,0.96237996 -2.29792,1.62393996 -3.6918,1.66181996 -1.24459,0.0927 -2.46671,-0.2491 -3.59505,-0.74812 -1.35789,-0.55965 -2.75133,-1.33436996 -4.27027,-1.18121996 -1.37741,0.14601 -2.41842,1.13685996 -3.44288,1.96782996 z" data-marker="adhoc" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
              <text x="55" y="45" text-anchor="middle" font="10px &quot;Arial&quot;" stroke="none" fill="#333333" style="text-anchor: middle; font-style: normal; font-variant: normal; font-weight: normal; font-stretch: normal; font-size: 12px; line-height: normal; font-family: Arial, Helvetica, sans-serif;" font-size="12px" font-family="Arial, Helvetica, sans-serif">
                <tspan dy="4">Ad-Hoc</tspan>
              </text>
            </svg>
        <a href="">
          <span class="glyphicon glyphicon-eye-open"></span>
        </a>
    </div>
</div>

<h3>Events</h3>
<table class="table table-responsive table-bordered bpmn-events">
  <tbody>
    <tr>
      <td>Type</td>
      <td colspan="3">Start</td>
      <td colspan="4">Intermediate</td>
      <td>End</td>
    </tr>
    <tr>
      <td></td>
      <td>Normal</td>
      <td>Event Sub-Process</td>
      <td>Event Sub-Process
        <br>
        non-interrupt
      </td>
      <td>Catch</td>
      <td>Boundary</td>
      <td>Boundary
        <br>
        non-interrupt
      </td>
      <td>Throw</td>
      <td></td>
    </tr>
    <tr>
      <td><a href="/bpmn-workflows/none-events/none-events.html">None</a></td>
      <td class="implemented">
        <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
          <g class="djs-visual">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1"></circle>
          </g>
        </svg>
      </td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td>
        <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
          <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="-webkit-tap-highlight-color: rgba(0, 0, 0, 0); stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
          <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
        </svg>
      </td>
      <td class="implemented">
        <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
          <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="-webkit-tap-highlight-color: rgba(0, 0, 0, 0); stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
        </svg>
      </td>
    </tr>
    <tr>
      <td><a href="/bpmn-workflows/message-events/message-events.html">Message</a></td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1"></circle>
              <path fill="#ffffff" stroke="#333333" d="M7,10L7,20L23,20L23,10ZM7,10L15,16L23,10" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)"></path>
            </g>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1"></circle>
              <path fill="#ffffff" stroke="#333333" d="M7,10L7,20L23,20L23,10ZM7,10L15,16L23,10" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)"></path>
            </g>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.5px;">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M7,10L7,20L23,20L23,10ZM7,10L15,16L23,10" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M7,10L7,20L23,20L23,10ZM7,10L15,16L23,10" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M7,10L7,20L23,20L23,10ZM7,10L15,16L23,10" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M7,10L7,20L23,20L23,10ZM7,10L15,16L23,10" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M7,9L15,15L23,9ZM7,10L7,20L23,20L23,10L15,16Z" stroke-width="2.1333333333333333" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9063)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M7,9L15,15L23,9ZM7,10L7,20L23,20L23,10L15,16Z" stroke-width="2.1333333333333333" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9063)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
    </tr>
    <tr>
      <td><a href="/bpmn-workflows/timer-events/timer-events.html">Timer</a></td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <circle cx="20" cy="20" r="10" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M15,5L15,8M20,6L18.5,9M24,10L21,11.5M25,15L22,15M24,20L21,18.5M20,24L18.5,21M15,25L15,22M10,24L11.5,21M6,20L9,18.5M5,15L8,15M6,10L9,11.5M10,6L11.5,9M17,8L15,15L19,15" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <circle cx="20" cy="20" r="10" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M15,5L15,8M20,6L18.5,9M24,10L21,11.5M25,15L22,15M24,20L21,18.5M20,24L18.5,21M15,25L15,22M10,24L11.5,21M6,20L9,18.5M5,15L8,15M6,10L9,11.5M10,6L11.5,9M17,8L15,15L19,15" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <circle cx="20" cy="20" r="10" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M15,5L15,8M20,6L18.5,9M24,10L21,11.5M25,15L22,15M24,20L21,18.5M20,24L18.5,21M15,25L15,22M10,24L11.5,21M6,20L9,18.5M5,15L8,15M6,10L9,11.5M10,6L11.5,9M17,8L15,15L19,15" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="10" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M15,5L15,8M20,6L18.5,9M24,10L21,11.5M25,15L22,15M24,20L21,18.5M20,24L18.5,21M15,25L15,22M10,24L11.5,21M6,20L9,18.5M5,15L8,15M6,10L9,11.5M10,6L11.5,9M17,8L15,15L19,15" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="10" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M15,5L15,8M20,6L18.5,9M24,10L21,11.5M25,15L22,15M24,20L21,18.5M20,24L18.5,21M15,25L15,22M10,24L11.5,21M6,20L9,18.5M5,15L8,15M6,10L9,11.5M10,6L11.5,9M17,8L15,15L19,15" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="10" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M15,5L15,8M20,6L18.5,9M24,10L21,11.5M25,15L22,15M24,20L21,18.5M20,24L18.5,21M15,25L15,22M10,24L11.5,21M6,20L9,18.5M5,15L8,15M6,10L9,11.5M10,6L11.5,9M17,8L15,15L19,15" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td></td>
      <td></td>
    </tr>
    <tr>
      <td>Conditional</td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M6,6L24,6L24,24L6,24L6,6M9,9L21,9M9,13L21,13M9,17L21,17M9,21L21,21Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M6,6L24,6L24,24L6,24L6,6M9,9L21,9M9,13L21,13M9,17L21,17M9,21L21,21Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M6,6L24,6L24,24L6,24L6,6M9,9L21,9M9,13L21,13M9,17L21,17M9,21L21,21Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M6,6L24,6L24,24L6,24L6,6M9,9L21,9M9,13L21,13M9,17L21,17M9,21L21,21Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M6,6L24,6L24,24L6,24L6,6M9,9L21,9M9,13L21,13M9,17L21,17M9,21L21,21Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M6,6L24,6L24,24L6,24L6,6M9,9L21,9M9,13L21,13M9,17L21,17M9,21L21,21Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td></td>
      <td></td>
    </tr>
    <tr>
      <td>Link</td>
      <td></td>
      <td></td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M9,13L18,13L18,10L23,15L18,20L18,17L8,17L8,13" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9688,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td></td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M9,13L18,13L18,10L23,15L18,20L18,17L8,17L8,13" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9688,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td></td>
    </tr>
    <tr>
      <td>Signal</td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M7.7124971,20.247342L22.333334,20.247342L15.022915000000001,7.575951200000001L7.7124971,20.247342Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9389,5.8695)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M7.7124971,20.247342L22.333334,20.247342L15.022915000000001,7.575951200000001L7.7124971,20.247342Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9389,5.8695)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M7.7124971,20.247342L22.333334,20.247342L15.022915000000001,7.575951200000001L7.7124971,20.247342Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9389,5.8695)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M7.7124971,20.247342L22.333334,20.247342L15.022915000000001,7.575951200000001L7.7124971,20.247342Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9389,5.8695)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M7.7124971,20.247342L22.333334,20.247342L15.022915000000001,7.575951200000001L7.7124971,20.247342Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9389,5.8695)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M7.7124971,20.247342L22.333334,20.247342L15.022915000000001,7.575951200000001L7.7124971,20.247342Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9389,5.8695)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M7.7124971,20.247342L22.333334,20.247342L15.022915000000001,7.575951200000001L7.7124971,20.247342Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9389,5.8695)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M7.7124971,20.247342L22.333334,20.247342L15.022915000000001,7.575951200000001L7.7124971,20.247342Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9389,5.8695)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
    </tr>
    <tr>
      <td><a href="/bpmn-workflows/error-events/error-events.html">Error</a></td>
      <td></td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M21.820839,10.171502L18.36734,23.58992L12.541380000000002,13.281818999999999L8.338651200000001,19.071607L12.048949000000002,5.832305699999999L17.996148000000005,15.132659L21.820839,10.171502Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9425,5.9194)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td></td>
      <td></td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M21.820839,10.171502L18.36734,23.58992L12.541380000000002,13.281818999999999L8.338651200000001,19.071607L12.048949000000002,5.832305699999999L17.996148000000005,15.132659L21.820839,10.171502Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9425,5.9194)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td></td>
      <td></td>
      <td class="implemented">
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M21.820839,10.171502L18.36734,23.58992L12.541380000000002,13.281818999999999L8.338651200000001,19.071607L12.048949000000002,5.832305699999999L17.996148000000005,15.132659L21.820839,10.171502Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9425,5.9194)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
    </tr>
    <tr>
      <td>Escalation</td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M15,7.75L21,22.75L15,16L9,22.75Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg" style="overflow: hidden; position: relative; left: -0.5px;">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M15,7.75L21,22.75L15,16L9,22.75Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M15,7.75L21,22.75L15,16L9,22.75Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M15,7.75L21,22.75L15,16L9,22.75Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M15,7.75L21,22.75L15,16L9,22.75Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M15,7.75L21,22.75L15,16L9,22.75Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9375,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
    </tr>
    <tr>
      <td>Termination</td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="10.5" fill="#333333" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
          </svg>
      </td>
    </tr>
    <tr>
      <td>Compensation</td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M14,8L14,22L7,15L14,8M21,8L21,22L14,15L21,8Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.875,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td></td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M14,8L14,22L7,15L14,8M21,8L21,22L14,15L21,8Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.875,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M14,8L14,22L7,15L14,8M21,8L21,22L14,15L21,8Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.875,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M14,8L14,22L7,15L14,8M21,8L21,22L14,15L21,8Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.875,5.9375)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
    </tr>
    <tr>
      <td>Cancel</td>
      <td></td>
      <td></td>
      <td></td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M6.283910500000001,9.27369L9.151395,6.4062062L14.886362000000002,12.141174L20.621331,6.4062056L23.488814,9.273689L17.753846,15.008657L23.488815,20.743626L20.621331,23.611111L14.886362000000002,17.876142L9.151394,23.611109L6.283911000000001,20.743625L12.018878,15.008658L6.283910500000001,9.27369Z" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td></td>
      <td></td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M6.283910500000001,9.27369L9.151395,6.4062062L14.886362000000002,12.141174L20.621331,6.4062056L23.488814,9.273689L17.753846,15.008657L23.488815,20.743626L20.621331,23.611111L14.886362000000002,17.876142L9.151394,23.611109L6.283911000000001,20.743625L12.018878,15.008658L6.283910500000001,9.27369Z" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(1,0,0,1,5,5)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
    </tr>
    <tr>
      <td>Multiple</td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M19.834856,21.874369L9.762008,21.873529L6.650126,12.293421000000002L14.799725,6.373429600000001L22.948336,12.294781L19.834856,21.874369Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.925,5.8827)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M19.834856,21.874369L9.762008,21.873529L6.650126,12.293421000000002L14.799725,6.373429600000001L22.948336,12.294781L19.834856,21.874369Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.925,5.8827)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M19.834856,21.874369L9.762008,21.873529L6.650126,12.293421000000002L14.799725,6.373429600000001L22.948336,12.294781L19.834856,21.874369Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.925,5.8827)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M19.834856,21.874369L9.762008,21.873529L6.650126,12.293421000000002L14.799725,6.373429600000001L22.948336,12.294781L19.834856,21.874369Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.925,5.8827)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M19.834856,21.874369L9.762008,21.873529L6.650126,12.293421000000002L14.799725,6.373429600000001L22.948336,12.294781L19.834856,21.874369Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.925,5.8827)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M19.834856,21.874369L9.762008,21.873529L6.650126,12.293421000000002L14.799725,6.373429600000001L22.948336,12.294781L19.834856,21.874369Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.925,5.8827)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M19.834856,21.874369L9.762008,21.873529L6.650126,12.293421000000002L14.799725,6.373429600000001L22.948336,12.294781L19.834856,21.874369Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.925,5.8827)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#333333" stroke="none" d="M19.834856,21.874369L9.762008,21.873529L6.650126,12.293421000000002L14.799725,6.373429600000001L22.948336,12.294781L19.834856,21.874369Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.925,5.8827)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
    </tr>
    <tr>
      <td>Multiple Parallel</td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M5.75,12L5.75,18L12,18L12,24.75L18,24.75L18,18L24.75,18L24.75,12L18,12L18,5.75L12,5.75L12,12Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9531,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M5.75,12L5.75,18L12,18L12,24.75L18,24.75L18,18L24.75,18L24.75,12L18,12L18,5.75L12,5.75L12,12Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9531,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <g class="djs-visual">
              <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
              <path fill="#ffffff" stroke="#333333" d="M5.75,12L5.75,18L12,18L12,24.75L18,24.75L18,18L24.75,18L24.75,12L18,12L18,5.75L12,5.75L12,12Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9531,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
            </g>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M5.75,12L5.75,18L12,18L12,24.75L18,24.75L18,18L24.75,18L24.75,12L18,12L18,5.75L12,5.75L12,12Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9531,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M5.75,12L5.75,18L12,18L12,24.75L18,24.75L18,18L24.75,18L24.75,12L18,12L18,5.75L12,5.75L12,12Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9531,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td>
          <svg height="40" version="1.1" width="40" xmlns="http://www.w3.org/2000/svg">
            <circle cx="20" cy="20" r="15" fill="#ffffff" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" id="svg_1" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <circle cx="20" cy="20" r="12" fill="none" stroke="#333333" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" stroke-dasharray="3,3" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></circle>
            <path fill="#ffffff" stroke="#333333" d="M5.75,12L5.75,18L12,18L12,24.75L18,24.75L18,18L24.75,18L24.75,12L18,12L18,5.75L12,5.75L12,12Z" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="1" transform="matrix(0.9375,0,0,0.9375,5.9531,5.9531)" style="stroke-linecap: round; stroke-linejoin: round; stroke-opacity: 1;"></path>
          </svg>
      </td>
      <td></td>
      <td></td>
    </tr>
  </tbody>
</table>
